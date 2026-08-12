# OrderControllerV2 演进接口性能测试方案（完整版）

> 基于 40 轮初步摸底的结果设计。目标：系统性回答"**每个演进阶段到底解决了什么、牺牲了什么、最佳适用场景是什么**"

---

## 一、测试目标（4 个问题）

| # | 问题 | 怎么答 |
|---|---|---|
| Q1 | 各策略的**真实业务吞吐上限**在哪？拐点何时出现？ | 找 QPS 开始下降的 VU 数 |
| Q2 | 高并发下**数据一致性**是否被破坏？ | 比对"库存扣减数"和"订单数"，看是否超卖 / 丢单 |
| Q3 | 各策略的**失败模式**是什么？ | 区分"业务拒绝"（库存不足 / 限流 / 单用户限购）vs"系统失败"（超时 / BindException / 5xx） |
| Q4 | 端到端**用户可感知延迟**分布？ | P50 / P95 / P99 在每档 VU 下的表现 |

---

## 二、现状摸底（已完成 40 轮）

已跑出关键矛盾，本方案针对性补强：

| 现状 | 矛盾 | 本方案怎么补 |
|---|---|---|
| cache_v5 QPS 7~37 | 源码有 `new ArrayList(10000)` 死代码 | Phase 1 修复后重测 |
| hash_url 业务订单 = 0 | 所有 VU 共享 userId=1 被"单用户限购"拦截 | Phase 2 用多 userId |
| MQ 业务成功率 0.3%~19% | 消费者是否真的处理了？ | Phase 3 加消息堆积监控 |
| wrong 业务成功率 <1% | 丢失更新严重但无量化 | 每轮记录"实际扣减 - 实际订单"差值 |

---

## 三、测试矩阵（5 维度）

### 3.1 接口策略（10 个）

| # | 策略 | 类型 | 演进位置 |
|---|---|---|---|
| 1 | wrongOrder | 反面教材 | 基线 |
| 2 | optimisticOrder | 单机乐观锁 | V1 |
| 3 | pessimisticOrder | 单机悲观锁 | V1 |
| 4 | cache_v1（先删缓存再扣库） | 缓存双写 | V2 |
| 5 | cache_v2（先扣库再删缓存） | 缓存双写 | V2 |
| 6 | cache_v3（V1 + 延迟双删） | 缓存一致性 | V3 |
| 7 | cache_v4（V2 + MQ 删缓存） | 缓存最终一致 | V4 |
| 8 | cache_v5（综合方案） | 终极方案 | V5 |
| 9 | createUserOrderWithMq | 异步削峰 | V-MQ |
| 10 | getVerifyHash + createOrderWithVerifiedUrl | hash 接口保护 | V-Hash |

### 3.2 并发档位（5 档）

| VUs | rampup | duration | 设计意图 |
|---:|---:|---:|---|
| 10 | 2s | 30s | 低负载基线，看"无竞争"性能 |
| 50 | 5s | 30s | 中负载，看常态吞吐 |
| 200 | 10s | 30s | 中高负载，开始饱和 |
| 500 | 25s | 30s | 高负载，找拐点 |
| 1000 | 50s | 30s | 极限压测，看崩溃模式 |

> 比之前多一档 10 VUs，**专门用于观察无竞争下的极限 RT**——这是判断"应用本身开销"和"锁开销"分量的关键。

### 3.3 库存压力（3 档）

| 档位 | 初始库存 | 设计意图 |
|---|---:|---|
| Low | 100 | 高争抢，库存快速耗尽 → 看"售罄后"行为 |
| Med | 1000 | 中争抢，多数请求能打中库存 |
| High | 10000 | 低争抢，纯测应用层吞吐 |

> 之前只测了 High 档。**Low 档是超卖检测的放大镜**——100 件库存被 1000 VUs 抢，wrongOrder 应该暴露得最彻底。

### 3.4 业务度量（核心改进）

每轮压测后自动采集 6 项业务指标（SQL 查询）：

```sql
USE m4a_miaosha;
SELECT
  (SELECT COUNT(*) FROM stock_order)       AS stock_orders,
  (SELECT COUNT(*) FROM ticket_order)      AS ticket_orders,
  (SELECT COUNT(*) FROM order_record)      AS order_records,
  (SELECT SUM(sale) FROM stock)            AS stock_deducted,
  (SELECT SUM(sold_count) FROM ticket)     AS ticket_deducted,
  (SELECT COUNT(*) FROM stock_order)
    + (SELECT COUNT(*) FROM order_record)  AS total_business_orders;
```

**关键一致性校验**：
- `超卖 = 实际订单 - 初始库存`（> 0 即超卖）
- `丢单 = 实际扣减 - 实际订单`（> 0 即丢单）
- `成功率(业务) = 实际订单 / HTTP 请求数 × 100%`

### 3.5 失败分类

每轮 JMeter 输出分类统计：

| 失败类型 | 识别规则 | 含义 |
|---|---|---|
| 库存耗尽 | response 含 "库存不足" | 正常业务拒绝 |
| 单用户限购 | response 含 "抢购过" / "不要太贪心" | 正常业务拒绝 |
| CAS 冲突 | response 含 "过期库存值" / "更新失败" | 乐观锁回滚 |
| Hash 校验失败 | response 含 "hash" / "不符合" | 接口保护生效 |
| BindException | code = "Non HTTP response code: java.net.BindException" | 客户端端口耗尽 |
| HTTP 5xx | code >= 500 | 服务端故障 |
| 超时 | elapsed > 10000 | 应用层过载 |

---

## 四、分阶段执行（4 个 Phase）

### Phase 1：修复 cache_v5 死代码（5 分钟）

**问题**：`OrderControllerV2.java` 中 `createOrderWithCacheV5` 方法体包含：
```java
List<Integer> list = new ArrayList(10000);
```
每次请求分配 10000 个元素的 ArrayList，导致 GC 频繁停顿 → QPS 跌到 7。

**动作**：
1. 删除该行
2. 重新 `mvn clean install -DskipTests`
3. 重启应用
4. 单独跑 cache_v5 @ 50/200 VUs 对比修复前后

**预期**：QPS 从 7 回升到 300+（与 cache_v1~v4 同档）。

---

### Phase 2：修 hash_url 的测试设计（10 分钟）

**问题**：所有 VU 共享 `userId=1`，被单用户限购拦截 → 业务订单 = 0。

**动作**：
1. MySQL 预置 2000 个测试用户（id=1~2000，name=test_N）
2. 修改 `hash_protected.jmx`：`userId = ${__threadNum}`（每线程一个用户）
3. 同步修改 `getVerifyHash` 和 `createOrderWithVerifiedUrl` 两处 userId

```sql
-- 预置测试用户（幂等）
INSERT IGNORE INTO user (id, user_name)
SELECT seq, CONCAT('test_', seq)
FROM (
  SELECT @row := @row + 1 AS seq
  FROM information_schema.columns a,
       information_schema.columns b,
       (SELECT @row := 0) r
  LIMIT 2000
) seq_table;
```

**预期**：hash_url 业务成功率从 0% 回升到 70%+（受限于 getVerifyHash 的 100MB 内存分配，仍是瓶颈）。

---

### Phase 3：MQ 消费者吞吐监控（5 分钟加埋点）

**问题**：MQ 接口 QPS 3944，但不知道消费者处理速度，可能消息堆积 → 用户最终拿不到订单。

**动作**：
1. 每轮压测前 / 中 / 后，执行：
   ```bash
   docker exec miaosha-rabbitmq rabbitmqctl list_queues name messages consumers
   ```
2. 在 `result/` 下保存 `mq_queue_<scenario>_<vu>.txt`
3. 报告新增列：`MQ积压`、`消费速率`

**预期**：
- 消费者处理速度 < 接口投递速度 → 队列持续增长
- 找到"消费能跟上"的 VU 上限

---

### Phase 4：完整 3D 矩阵（约 3 小时）

10 策略 × 5 VU 档 × 3 库存档 = **150 轮**（每轮 ~1 分钟，含 reset + run + collect）

**推荐简化**：先跑 **High 档 + 全 VU**（50 轮，约 50 分钟），看结果再决定是否加 Low/Med 档。

---

## 五、产出物

### 5.1 数据文件

```
jmeter/
├── result/
│   ├── <scenario>_<vu>_<inv>.jtl     # 原始采样（含 elapsed, success, code）
│   ├── <scenario>_<vu>_<inv>.log     # JMeter stdout
│   ├── <scenario>_<vu>_<inv>.biz.json # 业务指标（stock_orders, 超卖量...）
│   └── <scenario>_<vu>_<inv>.mq.txt  # MQ 队列状态
├── reports/
│   ├── REPORT_PHASE4_FULL.md         # 完整对比表（150 行）
│   ├── REPORT_CONSISTENCY.md         # 数据一致性专题
│   └── REPORT_FAILURE_MODES.md       # 失败模式专题
└── charts/
    ├── throughput_curves.png         # QPS 曲线（10 条线）
    ├── consistency_check.png         # 超卖 / 丢单矩阵
    └── latency_heatmap.png         # RT 热力图（VU × 策略）
```

### 5.2 报告模板

```markdown
## 吞吐对比
| 策略 | 10 VUs | 50 | 200 | 500 | 1000 | 峰值 VU | 峰值 QPS |
|---|---|---|---|---|---|---|---|

## 一致性对比
| 策略 | 总请求 | 库存扣减 | 订单数 | 超卖 | 丢单 | 业务成功率 |
|---|---|---|---|---|---|---|

## 失败模式
| 策略 | 业务拒绝% | CAS 冲突% | 系统失败% | 超时% |
|---|---|---|---|---|

## 延迟对比
| 策略 | Avg | P50 | P95 | P99 | Max |
|---|---|---|---|---|---|

## 演进结论
- 策略 X 解决了 Y，代价是 Z
- 推荐生产环境使用策略 A 或 B
```

---

## 六、执行顺序（建议）

| 阶段 | 内容 | 耗时 | 产出 |
|---|---|---|---|
| **Step 1** | Phase 1：修 cache_v5 | 10 min | cache_v5 真实吞吐 |
| **Step 2** | Phase 2：修 hash_url | 15 min | hash_url 真实吞吐 |
| **Step 3** | Phase 3：MQ 监控埋点 | 5 min | MQ 消费速率基线 |
| **Step 4** | 跑 High 档 + 5 VU（50 轮） | 50 min | 完整吞吐曲线 |
| **Step 5** | 分析 + 出 Phase 4 报告 | 30 min | 决策报告 |
| **Step 6**（可选） | 加 Low/Med 档（100 轮） | 100 min | 库存压力维度 |

**总计**：Step 1-5 = **2 小时**拿到完整结论；Step 6 = 再加 1.5 小时补强。

---

## 七、Exit Criteria（何时算"测完"）

- [ ] 10 个策略都有完整吞吐曲线（5 个 VU 档 × High 库存）
- [ ] 每个策略的"业务成功率"都测过，知道失败模式
- [ ] 至少一个策略暴露了**数据一致性问题**（超卖 / 丢单 / 重复扣减），并有证据
- [ ] cache_v5 和 hash_url 已修复并重测
- [ ] MQ 队列积压有数据（消费者速率 vs 生产者速率）
- [ ] 报告产出并给出"推荐生产环境使用哪个策略"的明确结论

---

## 八、风险与回退

| 风险 | 应对 |
|---|---|
| 应用崩溃 | 已备 `java -jar` 启动脚本；崩溃后 `docker compose restart app` |
| BindException 再现（高 VU） | 已配 JMeter keep-alive；必要时 OS 级 `net.inet.tcp.msl=1000` |
| MySQL 连接池耗尽 | 监控 `show status like 'Threads_connected'`，必要时调大连接池 |
| 单轮超时（> 5 min） | 自动 kill 并记录为 TIMEOUT，继续下一轮 |
| 报告解析脚本出错 | `parse_results.py` 异常时输出 raw CSV，人工兜底 |
