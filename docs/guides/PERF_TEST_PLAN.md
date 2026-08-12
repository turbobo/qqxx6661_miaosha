# OrderControllerV2 抢票接口压测方案

> 基于 `系统演进方案-qoder分析.md` 的 40 轮摸底数据制定  
> 目标：系统性验证 10 个演进策略的真实性能、业务成功率、数据一致性，给出生产选型结论

---

## 一、测试目标

### 1.1 必须回答的 5 个问题

| # | 问题 | 当前状态 | 本方案如何回答 |
|---|---|---|---|
| Q1 | 各策略的**真实业务吞吐上限**和拐点 | 40 轮摸底数据已有，但未完整 | 完整 3D 矩阵（策略×VU×库存） |
| Q2 | 高并发下**数据一致性**是否被破坏 | 部分验证（wrong 超卖） | 每轮 SQL 校验订单数 vs 库存扣减 |
| Q3 | 各策略的**失败模式**是什么 | 仅看 HTTP 成功率 | 区分业务拒绝 / CAS 冲突 / 系统失败 / 超时 |
| Q4 | **端到端延迟**（请求 → 订单落库） | 未测 | MQ 场景加延迟采样 |
| Q5 | MQ 消费者的真实处理速度 | 未测 | 监控 RabbitMQ 队列堆积 |

### 1.2 必须修复的 3 个阻塞问题

| # | 问题 | 影响 | 修复成本 |
|---|---|---|---|
| **F1** | CacheV5 的 `new ArrayList(10000)` 死代码 | QPS 跌到 7 | 删除 1 行 + 重新构建 |
| **F2** | CacheV5 的 `Thread.sleep(10000)` 模拟延迟 | RT 10 秒 | 删除 1 行 + 重新构建 |
| **F3** | Hash_url 测试设计缺陷（所有 VU 共享 userId=1） | 业务成功率 0% | 预置 2000 测试用户 + 改模板 |

不修复 F1/F2，CacheV5 的所有压测数据都无意义；不修复 F3，Hash 方案无法评估。

### 1.3 非目标

- 不做网络层压测（带宽/连接数上限）
- 不做数据库极限压测（单表行数上限）
- 不做容量规划（另案处理）

---

## 二、测试环境

### 2.1 拓扑

```
┌─────────────┐      ┌────────────────────┐      ┌────────────┐
│  JMeter     │─HTTP─│  Spring Boot 应用   │─TCP──│  MySQL 8   │
│  (单客户端) │      │  (miaosha-web)     │─TCP──│  Redis 6   │
│             │      │                    │─AMQP─│  RabbitMQ  │
└─────────────┘      └────────────────────┘      └────────────┘
       ↑                                            ↑
  localhost:8081                            docker compose (colima)
```

### 2.2 配置

| 组件 | 配置 | 备注 |
|---|---|---|
| 压测客户端 | 本机 JMeter 5.6.3 | 单客户端，避免多客户端协调开销 |
| 应用 | Spring Boot 2.2.5 + Java 8 | `java -jar miaosha-web-1.0.0-SNAPSHOT.jar` |
| MySQL | Docker 镜像 `mysql:8.0` | `count=10000` 默认，`sid=1~10` 隔离 |
| Redis | Docker 镜像 `redis:6-alpine` | 单实例 |
| RabbitMQ | Docker 镜像 `rabbitmq:3-management-alpine` | 含管理台 :15672 |
| OS | macOS (Apple Silicon) | colima 提供 Linux VM |

### 2.3 客户端瓶颈规避

摸底时发现 JMeter 单客户端在高 VU（>500）下出现 `BindException`（本机端口耗尽）。应对：

- JMeter 模板加 `use_keepalive=true`
- OS 级临时调整：`sudo sysctl net.inet.tcp.msl=1000`（缩短 TIME_WAIT）
- 每轮测试前 `lsof -i | wc -l` 监控端口数

---

## 三、测试矩阵（3 个维度）

### 3.1 维度 A：接口策略（10 个）

| # | 策略 | sid | 类型 | 演进位置 |
|---|---|---:|---|---|
| 1 | wrongOrder | 1 | 反面教材 | 基线 |
| 2 | optimisticOrder | 2 | 单机乐观锁 | V1 |
| 3 | pessimisticOrder | 3 | 单机悲观锁 | V1 |
| 4 | cache_v1 | 4 | 先删缓存再扣库 | V2 |
| 5 | cache_v2 | 5 | 先扣库再删缓存 | V2 |
| 6 | cache_v3 | 6 | V1 + 延迟双删 | V3 |
| 7 | cache_v4 | 7 | V2 + MQ 兜底删缓存 | V4 |
| 8 | cache_v5 | 8 | "终极方案"（修死代码后） | V5 |
| 9 | createUserOrderWithMq | 9 | MQ 异步削峰 | V-MQ |
| 10 | getVerifyHash + createOrderWithVerifiedUrl | 10 | Hash 接口保护 | V-Hash |

### 3.2 维度 B：并发档位（5 档）

| VUs | rampup | duration | 设计意图 |
|---:|---:|---:|---|
| 10 | 2s | 30s | 低负载基线，观察"无竞争"性能 |
| 50 | 5s | 30s | 中负载，常态吞吐 |
| 200 | 10s | 30s | 中高负载，开始饱和 |
| 500 | 25s | 30s | 高负载，找拐点 |
| 1000 | 50s | 30s | 极限压测，观察崩溃模式 |

### 3.3 维度 C：库存压力（3 档）

| 档位 | 初始库存 | 设计意图 |
|---|---:|---|
| Low | 100 | 高争抢，库存快速耗尽 → 放大超卖检测 |
| Med | 1000 | 中争抢，多数请求能打中库存 |
| High | 10000 | 低争抢，纯测应用层吞吐 |

### 3.4 总规模

**10 策略 × 5 VU × 3 库存 = 150 轮**  
每轮 ~1 分钟（含 reset + run + collect），**总耗时约 2.5 小时**。

**简化方案**（推荐首轮跑）：
- **Step 1**：仅跑 High 档（10 × 5 = 50 轮，~50 分钟）
- **Step 2**：根据 Step 1 结果决定是否加 Low/Med 档

---

## 四、数据隔离方案

### 4.1 库存隔离

每个策略使用独立的 sid，避免共享库存导致场景间污染：

| sid | stock.name | 用途 |
|---:|---|---|
| 1 | iphone | wrongOrder |
| 2 | mac | optimisticOrder |
| 3 | ipad | pessimisticOrder |
| 4 | airpods | cache_v1 |
| 5 | watch | cache_v2 |
| 6 | homepod | cache_v3 |
| 7 | tv | cache_v4 |
| 8 | vision | cache_v5 |
| 9 | pencil | MQ |
| 10 | airtag | Hash |

### 4.2 Reset 脚本（每轮前执行）

```sql
-- stock 老系统（V2 WrongOrder / Optimistic / Pessimistic / CacheV1-V4 用）
UPDATE stock SET count = {库存档位}, sale = 0;
DELETE FROM stock_order;

-- ticket 新系统（CacheV5 / MQ / VerifiedUrl 用）
UPDATE ticket SET remaining_count = total_count, sold_count = 0;
DELETE FROM ticket_order;
DELETE FROM ticket_purchase_record;
DELETE FROM order_record;
```

加 `FLUSHALL` 清空 Redis。

---

## 五、监控与指标采集

### 5.1 业务指标（每轮后 SQL 采集）

| 指标 | SQL 查询 | 用途 |
|---|---|---|
| 实际订单数 | `SELECT COUNT(*) FROM stock_order` | 业务成功数 |
| 库存扣减数 | `SELECT SUM(sale) FROM stock WHERE id={sid}` | 验证与订单数一致 |
| **超卖量** | `订单数 - 初始库存`（>0 即超卖） | **数据一致性核心校验** |
| **丢单量** | `库存扣减数 - 订单数`（>0 即丢单） | **数据一致性核心校验** |
| 业务成功率 | `订单数 / HTTP 请求数 × 100%` | 区别于 HTTP 成功率 |

### 5.2 HTTP 指标（JMeter 自动采集）

| 指标 | 来源 | 用途 |
|---|---|---|
| 总请求数 | JMeter summary | 吞吐基线 |
| HTTP 成功率 | JMeter success% | 系统稳定性 |
| Avg / P50 / P95 / P99 RT | JMeter elapsed | 延迟分布 |
| 错误率分类 | JMeter failureMessage | 失败归因 |

### 5.3 失败模式分类（关键字匹配）

| 失败类型 | 识别规则 | 含义 |
|---|---|---|
| 库存耗尽 | response 含"库存不足" | 正常业务拒绝 |
| 单用户限购 | response 含"抢购过" / "不要太贪心" | 正常业务拒绝 |
| CAS 冲突 | response 含"过期库存值" / "更新失败" | 乐观锁回滚 |
| Hash 校验失败 | response 含"hash" / "不符合" | 接口保护生效 |
| BindException | code = "Non HTTP response code: java.net.BindException" | 客户端端口耗尽 |
| HTTP 5xx | code >= 500 | 服务端故障 |
| 超时 | elapsed > 10000 | 应用层过载 |

### 5.4 资源监控（可选）

| 组件 | 监控项 | 命令 |
|---|---|---|
| MySQL | Threads_connected | `show status like 'Threads_connected'` |
| MySQL | Slow queries | `show variables like 'slow_query_log'` |
| Redis | 内存使用 | `INFO memory` |
| RabbitMQ | 队列堆积 | `rabbitmqctl list_queues name messages consumers` |
| JVM | GC 停顿 | `-XX:+PrintGCDetails` 启动参数 |

---

## 六、分阶段执行（4 个 Phase）

### Phase 1：修复阻塞问题（30 分钟）

| 任务 | 动作 | 验证 |
|---|---|---|
| F1 删死代码 | `OrderControllerV2.java:309` 删 `new ArrayList(10000)` | 代码 review |
| F2 删 sleep | `OrderServiceImpl.java:289` 删 `Thread.sleep(10000)` | 代码 review |
| F3 修 hash 测试 | 预置 2000 用户 + 改模板 userId=${__threadNum} | curl 单次测试通 |
| 重新构建 | `mvn clean install -DskipTests` | jar 文件更新 |
| 重启应用 | 停旧进程 + 启新进程 | `lsof -i :8081` |
| **CacheV5 单点验证** | 跑 50 VUs × 15s，对比修复前后 QPS | QPS 应回升到 250+ |

### Phase 2：最小验证（10 分钟）

跑 **3 个核心策略 × 50 VUs × High 档**，验证：

| 策略 | 期望结果 | 实际 |
|---|---|---|
| pessimistic | QPS 300±50，业务成功率 100% | |
| mq | QPS 3000±500，业务成功率 >15% | |
| hash_url | QPS 100±50，业务成功率 >60% | |

如结果与摸底数据偏差 >30%，回退排查环境问题。

### Phase 3：完整矩阵（50~150 轮）

**推荐首轮：仅 High 档（50 轮，约 50 分钟）**

```bash
./run_evolution.sh full
```

每轮自动执行：
1. Reset（清 DB + Redis）
2. JMeter 运行
3. 业务指标 SQL 采集
4. 结果写入 `result/<strategy>_<vu>.jtl` + `biz.json`

**根据 Phase 3 结果决定是否加 Low/Med 档**（约额外 100 轮，100 分钟）。

### Phase 4：专项深挖（可选，30 分钟）

根据 Phase 3 发现的异常点深挖：

| 异常 | 深挖方案 |
|---|---|
| 某策略业务成功率异常低 | 抽样失败请求，分析 response 内容 |
| 某策略 RT P99 异常高 | 开启 GC 日志，对比 GC 停顿 |
| MQ 业务成功率 <10% | 监控队列堆积，调整消费者并发数 |
| 出现超卖 | 立即停止，分析锁机制是否失效 |

---

## 七、数据采集与报告

### 7.1 每轮产出文件

```
jmeter/result/
├── <strategy>_<vu>_<inv>.jtl       # JMeter 原始采样（含 elapsed, success, code）
├── <strategy>_<vu>_<inv>.log       # JMeter stdout
└── <strategy>_<vu>_<inv>.biz.json  # 业务指标（订单数, 库存扣减, 超卖, 丢单）
```

### 7.2 报告产出（4 份）

#### 报告 1：吞吐对比（`REPORT_THROUGHPUT.md`）

| 策略 | 10 VUs QPS | 50 | 200 | 500 | 1000 | 峰值 VU | 峰值 QPS |
|---|---|---|---|---|---|---|---|
| pessimistic | | | | | | | |
| optimistic | | | | | | | |
| ... | | | | | | | |

#### 报告 2：一致性校验（`REPORT_CONSISTENCY.md`）

| 策略 | 总请求 | 库存扣减 | 订单数 | **超卖** | **丢单** | 业务成功率 |
|---|---|---|---|---|---|---|
| pessimistic | | | | 0 | 0 | 100% |
| wrong | | | | ? | ? | ?% |
| ... | | | | | | |

#### 报告 3：失败模式（`REPORT_FAILURE_MODES.md`）

| 策略 | 业务拒绝% | CAS 冲突% | 系统失败% | 超时% |
|---|---|---|---|---|
| pessimistic | 0% | 0% | 0% | 0% |
| optimistic | ?% | ?% | ?% | ?% |
| ... | | | | |

#### 报告 4：延迟对比（`REPORT_LATENCY.md`）

| 策略 | Avg | P50 | P95 | P99 | Max |
|---|---|---|---|---|---|
| pessimistic | | | | | |
| optimistic | | | | | |
| ... | | | | | |

### 7.3 可视化（可选）

- **吞吐曲线图**：X 轴 VU，Y 轴 QPS，10 条线对比
- **一致性热力图**：策略 × VU 矩阵，颜色表示超卖/丢单程度
- **延迟分布箱线图**：每策略一个箱线图

---

## 八、退出标准

- [ ] 10 个策略都有完整吞吐曲线（5 个 VU 档 × High 库存）
- [ ] 每个策略的"业务成功率"都测过，知道失败模式
- [ ] **至少一个策略暴露数据一致性问题**（超卖 / 丢单 / 重复扣减），并有证据
- [ ] CacheV5 已修复并重测，对比修复前后
- [ ] Hash_url 已修复并重测，业务成功率 >60%
- [ ] MQ 队列积压有数据（消费者速率 vs 生产者速率）
- [ ] 4 份报告产出，并给出"生产环境推荐哪个策略"的明确结论

---

## 九、风险与应对

| 风险 | 影响 | 应对 |
|---|---|---|
| 应用崩溃 | 单轮中断 | 自动重启，标记本轮 TIMEOUT，继续下一轮 |
| BindException 再现 | 高 VU 数据失真 | OS 级 `net.inet.tcp.msl=1000`，必要时降 VU |
| MySQL 连接池耗尽 | 5xx 错误激增 | 监控 `Threads_connected`，必要时调大连接池 |
| 单轮超时（> 5 min） | 总时长失控 | 自动 kill，标记 TIMEOUT，继续下一轮 |
| 报告解析脚本出错 | 数据丢失 | `parse_results.py` 异常时输出 raw CSV，人工兜底 |
| 数据一致性严重问题（超卖） | 业务风险 | **立即停止**，标记该策略为"不可用"，不进入生产候选 |

---

## 十、时间表（推荐节奏）

| 阶段 | 内容 | 预计耗时 | 产出 |
|---|---|---|---|
| **Phase 1** | 修复 F1/F2/F3 | 30 min | CacheV5 + hash_url 修复完成 |
| **Phase 2** | 最小验证（3 策略 × 50 VUs） | 10 min | 环境正常确认 |
| **Phase 3** | High 档完整矩阵（50 轮） | 50 min | 4 份报告初版 |
| **Phase 3'** | （可选）Low + Med 档（100 轮） | 100 min | 库存压力维度 |
| **Phase 4** | 专项深挖（根据 Phase 3 异常） | 30 min | 异常原因分析 |
| **收尾** | 报告整合 + 结论产出 | 30 min | 最终生产选型建议 |

**总计**：
- 最小闭环（Phase 1-3 + 收尾）：**2 小时**
- 完整闭环（加 Phase 3' + 4）：**4 小时**

---

## 十一、交付物

| 交付物 | 状态 | 责任人 |
|---|---|---|
| F1/F2 代码修复 PR | ⏳ 待修 | 开发 |
| F3 hash 测试修复 PR | ⏳ 待修 | 开发 |
| 完整压测脚本（基于 miaosha 框架） | ⏳ 待开发 | 开发 |
| 40 轮摸底数据（已产出） | ✅ 已完成 | - |
| 150 轮完整压测数据 | ⏳ 待跑 | 开发 |
| 4 份专题报告 | ⏳ 待产出 | 开发 |
| **最终生产选型建议**（核心交付） | ⏳ 待产出 | 开发 + 架构 |

---

## 十二、与摸底阶段的关键差异

| 维度 | 摸底（40 轮） | 本方案（150 轮） |
|---|---|---|
| 业务度量 | 只看 `stock.sale` | 6 项 SQL 指标 + 超卖/丢单校验 |
| 失败归因 | 只看 HTTP 成功率 | 6 类失败分别统计 |
| 并发维度 | 4 档（50~1000） | 5 档（加 10 VU 无竞争基线） |
| 库存维度 | 单档 10000 | 3 档（100/1000/10000） |
| 修复项 | 无 | 3 个具体 Phase 修复现有 bug |
| 报告 | 单表 | 4 份专题报告 + 3 张图 |
| 退出标准 | 无 | 7 项明确 Exit Criteria |

---

## 十三、附：摸底数据（作为对比基线）

| 策略 | 50 VUs QPS | 200 | 500 | 1000 | 业务成功率 |
|---|---:|---:|---:|---:|---:|
| mq | 2786 | **3944** | 3617 | 2225 | 0.3%~19% |
| optimistic | 4146 | 4016 | 3644 | 2052 | 1.1%~1.7% |
| wrong | 3158 | 2933 | 2593 | 1569 | 0.6%~1.1% |
| pessimistic | 328 | 339 | 340 | 213 | **94%~100%** |
| cache_v1~v4 | 269~319 | 280~313 | 287~337 | 180~203 | 98%~100% |
| cache_v5 | 7 | 20 | 37 | 32 | 0.4%~2% |
| hash_url | 9 | 23 | 40 | 32 | 0% |

> 本方案 Phase 2 的最小验证将以本表为对照基线，偏差 >30% 即触发环境排查。
