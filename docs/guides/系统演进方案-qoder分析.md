# OrderControllerV2 抢票接口演进深度分析

> 基于 40 轮 JMeter 压测数据（50/200/500/1000 VUs × 10 策略）+ `OrderServiceImpl.java` 全量源码  
> 分析范围：WrongOrder → 锁策略 → 缓存一致性（V1~V4）→ V5 "终极方案" → MQ 异步削峰 → Hash 接口保护

---

## 一、演进地图（一张图看全貌）

```
                        ┌─ WrongOrder (反面教材)
                        │
   并发安全  ─── 锁策略 ─┤─ OptimisticOrder (CAS 乐观锁)
                        │
                        └─ PessimisticOrder (悲观锁 FOR UPDATE)
                                │
                                ↓
                        ┌─ CacheV1 (先删缓存再扣库)
                        │
   缓存一致  ── 双写策略 ┤─ CacheV2 (先扣库再删缓存)
                        │
                        ├─ CacheV3 (V1 + 延迟双删)
                        │
                        └─ CacheV4 (V2 + MQ 兜底删缓存)
                                │
                                ↓
                        CacheV5 ("终极方案" = 乐观锁+缓存+MQ)   ⚠️ 性能崩塌
                                │
                                ↓
                   ┌── createUserOrderWithMq (异步削峰)     ★ 吞吐之王
                   │
   异步 + 保护  ───┤
                   └── getVerifyHash + createOrderWithVerifiedUrl (接口防护)
```

6 个阶段解决的问题依次是：**并发安全 → 缓存一致性 → 综合方案 → 异步削峰 → 接口保护**。

---

## 二、逐版本拆解

### 阶段 0：WrongOrder（反面教材）

**源码**（`OrderServiceImpl.java:52-60`）：
```java
public int createWrongOrder(int sid) {
    Stock stock = checkStock(sid);      // SELECT
    saleStock(stock);                    // UPDATE sale = sale+1
    int id = createOrder(stock);         // INSERT
    return id;
}
```

**问题**：三步操作无原子性。高并发下两个线程读到同一份库存，都执行 `sale+1`，结果只扣了 1 件。

**实测数据**（50 VUs）：
- HTTP 总请求 15741，HTTP 成功 100%
- **实际落库订单 888 件，业务成功率 5.6%**
- 其余 14853 次扣减都被并发覆盖了

**演进价值**：作为对比基线，证明"不加锁"的代价。

---

### 阶段 1：锁策略（悲观 vs 乐观）

#### 1.1 PessimisticOrder（悲观锁）

**源码**（`OrderServiceImpl.java:141-153`）：
```java
@Transactional
public int createPessimisticOrder(int sid) {
    Stock stock = checkStockForUpdate(sid);  // SELECT ... FOR UPDATE
    saleStock(stock);                         // UPDATE
    createOrder(stock);                       // INSERT
    return stock.getCount() - (stock.getSale());
}
```

**关键**：`SELECT ... FOR UPDATE` 把这一行锁住，其他事务必须等锁释放。同一事务内所有操作共享同一把行锁（注释里有说明）。

**实测数据**：
- 50 VUs → QPS 328，业务成功率 100%
- 1000 VUs → QPS 213，业务成功率 94.2%
- **最稳定**：50~500 VUs QPS 波动 <5%

**演进价值**：证明"加锁"能解决并发问题，但吞吐受限。

#### 1.2 OptimisticOrder（乐观锁）

**源码**（`OrderServiceImpl.java:63-74`）：
```java
public int createOptimisticOrder(int sid) {
    Stock stock = checkStock(sid);                   // SELECT（无锁）
    boolean success = saleStockOptimistic(stock);    // UPDATE ... WHERE version=?
    if (!success) {
        throw new RuntimeException("过期库存值，更新失败");
    }
    createOrder(stock);
    return stock.getCount() - (stock.getSale() + 1);
}
```

**关键**：靠 `version` 字段做 CAS 校验——更新时检查版本号是否和查询时一致。冲突则抛异常回滚。

**实测数据**：
- 50 VUs → HTTP QPS 4146，**业务成功率仅 1.1%**（CAS 冲突率 97%+）
- 真实业务 QPS 13，**比悲观锁慢 25 倍**

**反直觉结论**：高并发下乐观锁反而比悲观锁慢。因为 CAS 冲突率太高——50 个线程同时读同一个版本号，49 个会冲突回滚，回滚的请求白跑一趟。

**演进价值**：证明"乐观锁不是万能药"，**高争用场景下悲观锁更合适**。

---

### 阶段 2：缓存一致性（V1~V4）

悲观锁吞吐受限（QPS ~300），瓶颈在 DB。自然的思路：**把读操作挡在 Redis 缓存前**。但引入缓存就带来新问题——**缓存和数据库怎么保持一致？**

#### 2.1 CacheV1（先删缓存，再扣库）

**源码**（`OrderControllerV2.java:209-225`）：
```java
stockService.delStockCountCache(sid);           // 1. 删缓存
count = orderService.createPessimisticOrder(sid); // 2. 扣库（悲观锁）
```

**问题**：两步之间如果有读请求，会从 DB 读旧值写回缓存 → 脏数据。

**实测数据**：QPS 318，业务成功率 100%（和悲观锁几乎一样）。**缓存清理的耗时相比锁等待可以忽略**。

#### 2.2 CacheV2（先扣库，再删缓存）

**源码**（`OrderControllerV2.java:231-248`）：
```java
count = orderService.createPessimisticOrder(sid); // 1. 扣库
stockService.delStockCountCache(sid);             // 2. 删缓存
```

**业界共识**：比 V1 安全。原因：步骤 2 失败时，最多是旧缓存残留，不会有脏数据。但仍有短暂不一致窗口。

**实测数据**：QPS 319，业务成功率 100%（和 V1 几乎一样）。

#### 2.3 CacheV3（V1 + 延迟双删）

**源码**（`OrderControllerV2.java:253-276`）：
```java
stockService.delStockCountCache(sid);             // 1. 删缓存
count = orderService.createPessimisticOrder(sid); // 2. 扣库
cachedThreadPool.execute(new delCacheByThread(sid)); // 3. 1 秒后再删一次
```

**思路**：第 3 步兜底，把"步骤 1 和步骤 2 之间可能被写回的脏缓存"再清掉。

**实测数据**：QPS 305，业务成功率 100%。

#### 2.4 CacheV4（V2 + MQ 兜底删缓存）

**源码**（`OrderControllerV2.java:280-308`）：
```java
count = orderService.createPessimisticOrder(sid); // 1. 扣库
stockService.delStockCountCache(sid);             // 2. 删缓存
cachedThreadPool.execute(new delCacheByThread(sid)); // 3. 延迟再删
sendToDelCache(String.valueOf(sid));              // 4. MQ 兜底再删
```

**思路**：万一延迟双删也失败了（比如线程池拒绝任务），通过 RabbitMQ 让专门的消费者去删缓存，最终一致。

**实测数据**：QPS 269，业务成功率 100%。

---

### 阶段 3：V1~V4 的总结论

**实测发现**：4 个版本 QPS 都在 269~319 之间，**差距 <20%**。

**根本原因**：瓶颈在悲观锁（QPS ~300），缓存清理那点耗时（毫秒级）相比锁等待时间（100~200ms）可以忽略。所以"缓存一致性"的演进**不提升吞吐，只提升一致性**。

**演进价值**：V1~V4 解决的是"缓存和 DB 短暂不一致"的工程问题，不是性能问题。

---

### 阶段 4：CacheV5 "终极方案"（性能灾难）

**源码**（`OrderControllerV2.java:330-363`）：
```java
public String createOrderWithCacheV5(...) {
    count = orderService.createOptimisticOrder(sid, userId);  // 乐观锁
    stockService.delStockCountCache(sid);                     // 删缓存
    cachedThreadPool.execute(new delCacheByThread(sid));      // 延迟双删
    sendToDelCache(String.valueOf(sid));                      // MQ 兜底
    List<Integer> list = new ArrayList(10000);                // ⚠️ 死代码
    return "购买成功";
}
```

**设计思路**：
- 用乐观锁替换悲观锁（理论上吞吐更高）
- 保留 V4 的缓存清理三件套
- 调用 `createOptimisticOrder(sid, userId)` 重载版本，这个版本包含完整的用户校验 + MQ 异步下单 + order_record 写入

**实测数据**：**QPS 7~37，平均 RT 10 秒**（撞超时）。

**根因**：第 309 行 `List<Integer> list = new ArrayList(10000)` 每次请求分配一个 10000 元素的 ArrayList，给 GC 造成巨大压力。这是个遗留的调试代码（作者可能在测"大对象对 JVM 的影响"），忘了删。

**另一个问题**：`createOptimisticOrder(sid, userId)` 重载里有 `Thread.sleep(10000)`（第 289 行）——模拟 MQ 消费者排队 10 秒。**每个请求强制睡 10 秒**，这直接解释了 RT 10 秒的现象。

**演进价值**：**反面教材 #2**——展示了"堆砌所有好技术不等于好方案"。

---

### 阶段 5：MQ 异步削峰（吞吐之王）

**源码**（`OrderControllerV2.java:396-430`）：
```java
public String createUserOrderWithMq(Integer sid, Long userId) {
    // 1. 检查用户是否已抢购过（缓存）
    Boolean hasOrder = orderService.checkUserOrderInfoInCache(sid, userId);
    if (hasOrder != null && hasOrder) {
        return "你已经抢购过了，不要太贪心.....";
    }
    // 2. 检查库存（缓存）
    Integer count = stockService.getStockCount(sid);
    if (count == 0) {
        return "秒杀请求失败，库存不足.....";
    }
    // 3. 把订单丢进 MQ
    JSONObject jsonObject = new JSONObject();
    jsonObject.put("sid", sid);
    jsonObject.put("userId", userId);
    sendToOrderQueue(jsonObject.toJSONString());
    return "秒杀请求提交成功";
}
```

**思路**：
- 接口只做 3 件事：查缓存、查缓存、扔消息。**完全不碰 DB**。
- 真正的扣库 + 创建订单由 MQ 消费者异步处理。

**实测数据**：
- 200 VUs → **QPS 3944**（悲观锁的 11.6 倍）
- Avg RT 43ms（悲观锁 496ms 的 1/11）

**但业务成功率只有 0.3%~19%**：
- 50 VUs 下所有 VU 共享 userId=1，被"单用户限购"拦截
- MQ 消费者是否真的处理了？需要 Phase 3 验证

**演进价值**：证明"接口只做入队，让下游异步处理"是吞吐最优解。但引入了新问题——**端到端延迟**（用户提交请求 → 订单真正落库的时间）。

---

### 阶段 6：Hash 接口保护

**源码**（`OrderControllerV2.java:134-177`）：
```java
// Step 1: 先拿 hash
public String getVerifyHash(Integer sid, Long userId) {
    // 1. 抢购时间校验
    LocalTime now = LocalTime.now();
    if (now.isBefore(START_TIME) || now.isAfter(END_TIME)) throw ...;
    // 2. 用户校验
    User user = userMapper.selectByPrimaryKey(userId);
    // 3. 商品校验
    Stock stock = stockService.getStockById(sid);
    // 4. 生成 hash，存 Redis，返回给客户端
    String hash = createVerifyHash(sid, userId);
    stringRedisTemplate.opsForValue().set(hashKey, hash, 60, TimeUnit.SECONDS);
    return hash;
}

// Step 2: 带 hash 下单
public String createOrderWithVerifiedUrl(sid, userId, verifyHash) {
    // 1. 验证 hash 和 Redis 一致
    String hashInRedis = stringRedisTemplate.opsForValue().get(hashKey);
    if (!verifyHash.equals(hashInRedis)) throw ...;
    // 2. 校验用户、商品
    // 3. 乐观锁扣库 + 创建订单
    saleStockOptimistic(stock);
    createOrderWithUserInfoInDB(stock, userId);
}
```

**思路**：
- 两步走：先拿一次性 token（hash），再带 token 下单
- 防止：刷接口、脚本抢票、恶意遍历 URL
- 内部仍用乐观锁扣库

**实测数据**：QPS 9~40，业务成功率 0%（测试设计缺陷，不是方案本身的问题）。

**演进价值**：在 MQ 方案之前加一道"防刷墙"，属于**接口层安全**而不是性能优化。

---

## 三、演进的本质规律

### 3.1 每一代都在解决上一代的痛点

| 代际 | 痛点 | 解决方案 | 引入的新问题 |
|---|---|---|---|
| Wrong → 锁策略 | 并发丢数据 | 加锁 | 吞吐受限 |
| 悲观锁 → 乐观锁 | 悲观锁吞吐低 | CAS | 高争用下冲突率高 |
| 锁 → 缓存 | DB 成瓶颈 | 把读挡在 Redis 前 | 缓存一致性 |
| V1 → V2 → V3 → V4 | 缓存和 DB 短暂不一致 | 双删 / MQ 兜底 | 工程复杂度 |
| V4 → V5 | 想要"全都要" | 堆砌所有好技术 | 性能崩塌 |
| V5 → MQ 方案 | 同步 DB 是瓶颈 | 完全异步 | 端到端延迟 |
| 同步 → Hash 保护 | 脚本刷接口 | 一次性 token | 用户多一步操作 |

### 3.2 三个反直觉的"真相"

**真相 1：HTTP QPS ≠ 业务 QPS**
- wrong 的 HTTP QPS 看起来最快（3158），但业务 QPS 只有 59
- 只看 HTTP 成功率会被严重误导

**真相 2：乐观锁不一定比悲观锁快**
- 高争用（>10 个线程争同一行）下，CAS 冲突率 >85%，乐观锁业务 QPS 反而更低
- **乐观锁适合"争用少、冲突概率低"的场景**

**真相 3：堆砌"好技术"不等于好方案**
- V5 把乐观锁 + 缓存 + MQ + 双删 全用上，结果 QPS 7
- **架构不是越复杂越好，而是越匹配场景越好**

### 3.3 演进方向总结（两条线并行）

```
性能优化线：Wrong → Pessimistic → Cache V1-V4 → MQ（一路提升吞吐）
                       ↓
               一致性保护线：Wrong → Pessimistic → Cache V1-V4 → Hash（一路提升数据正确性）
                       ↓
               V5 把两条线合流，但堆砌导致性能崩塌
                       ↓
               最终答案：MQ 异步（性能） + Hash 保护（安全） 分开做
```

---

## 四、代码质量观察

除了演进设计，源码还有几个值得指出的问题：

| 问题 | 位置 | 严重度 |
|---|---|---|
| `new ArrayList(10000)` 死代码 | `OrderControllerV2.java:309` | 🔴 性能崩塌 |
| `Thread.sleep(10000)` 模拟延迟 | `OrderServiceImpl.java:289` | 🔴 RT 10 秒 |
| `getVerifyHash` 里 `byte[] a = new byte[100MB]` | `OrderControllerV2.java:139` | 🟡 GC 压力 |
| `insertOrderRecord` 逻辑反了（`null != orderRecord` 时 return） | `OrderServiceImpl.java:444` | 🔴 订单永远不写入 |
| `selectOrderRecordList` 逻辑也反了 | `OrderServiceImpl.java:452` | 🔴 查询永远返回 null |
| `updateOrderRecordStatus` 用了 `&&` 而非 `||` | `OrderServiceImpl.java:468` | 🟡 状态更新条件错误 |

**前 3 个直接影响性能**，后 3 个是逻辑 bug，会破坏业务正确性。

---

## 五、给生产环境的选型建议

基于实测数据，按场景推荐：

| 业务场景 | 推荐方案 | 理由 |
|---|---|---|
| 低并发 + 高一致性 | PessimisticOrder | 稳定 300 QPS，100% 成功率，简单可靠 |
| 中并发 + 强一致 | CacheV2 或 V3 | 在悲观锁基础上加缓存读，不引入太多复杂度 |
| 秒杀场景（瞬时高峰） | MQ 异步削峰 + Hash 保护 | 吞吐 3944 QPS，接口快速响应，下游异步消费 |
| 任何场景都**不该**用 | CacheV5 | 死代码 + sleep 导致性能崩塌 |

**终极答案**：**没有"最优方案"，只有"最匹配场景的方案"**。演进的意义不在于"V5 一定比 V4 好"，而在于"每代都在解决特定场景的特定问题"。

---

## 六、后续待深入

1. **MQ 消费者的真实吞吐量**（接口入队 3944 QPS，但消费者处理速度未知）
2. **端到端延迟**（用户点"抢购" → 订单真正落库的总时间）
3. **V5 修复死代码后的真实性能**（预计能回到 V1~V4 水平）
4. **不同库存压力下的策略反转**（Low 档下乐观锁可能赢）

---

## 七、附：实测数据汇总（40 轮，50~1000 VUs）

| 策略 | 50 VUs QPS | 200 | 500 | 1000 | 业务成功率 |
|---|---:|---:|---:|---:|---:|
| mq | 2786 | **3944** | 3617 | 2225 | 0.3%~19% |
| optimistic | 4146 | 4016 | 3644 | 2052 | 1.1%~1.7% |
| wrong | 3158 | 2933 | 2593 | 1569 | 0.6%~1.1% |
| pessimistic | 328 | 339 | 340 | 213 | **94%~100%** |
| cache_v1~v4 | 269~319 | 280~313 | 287~337 | 180~203 | 98%~100% |
| cache_v5 | 7 | 20 | 37 | 32 | 0.4%~2% |
| hash_url | 9 | 23 | 40 | 32 | 0% |

> 数据来源：`jmeter/reports/REPORT.md`（完整 40 行明细）  
> 压测脚本：`jmeter/run_evolution.sh`  
> 场景隔离：每个策略使用独立 sid，库存 10000 件
