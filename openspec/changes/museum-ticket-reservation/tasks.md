## 1. 基础设施与模块骨架

- [ ] 1.1 在 miaosha-dao/service/web 下新建独立包（如 `museum`），与现有抢购类物理隔离
- [ ] 1.2 编写独立 DDL 脚本：`ticket_quota`、`ticket_order`、`outbox_message`、`ticket_reserve_log`（不改动现有 miaosha 表）
- [ ] 1.3 生成 4 张新表的实体与 Mapper（miaosha-dao）
- [ ] 1.4 新增配置项：`OPEN_DAYS`、每日默认配额、分段数 N、熔断阈值（成功率/超时/恢复），并接入 profile
- [ ] 1.5 声明独立 RabbitMQ 资源：`visitor.reserve.*`、`ticket.reserve.reply.*`（及方案 B 的 `ticket.order.*`）与各自死信队列，持久化 + 手动 ACK

## 2. 分段库存（date-partitioned-stock）

- [ ] 2.1 设计分段 Redis key 空间：`museum:stock:{date}:seg{n}`、`museum:stock:total:{date}`、`museum:user:grabbed:{date}`
- [ ] 2.2 编写扣减 Lua 脚本：sismember 限购检查 → 命中段扣减 → 段满顺时针探测其余段 → sadd 标记 → 全段无货返回售罄
- [ ] 2.3 编写回补逻辑：按消息携带的 segId 精确 `incr` 回原段 + `srem` 移除用户标记
- [ ] 2.4 实现每日放票定时任务：从 `now()` 起算 `1..OPEN_DAYS`，重复放票幂等（已存在则不覆盖）
- [ ] 2.5 提供运营配额配置读取（特展加放/闭馆置 0），放票时按配额初始化各段（总量按段均分）
- [ ] 2.6 单元测试：超卖防护（并发扣减总数 ≤ 库存）、单用户限购、段间余量探测、回补回原段、放票/校验窗口一致

## 3. 抢票主流程（ticket-grab）

- [ ] 3.1 实现本地 JWT 验签（RS256 + 缓存访客系统公钥/JWKS + 定时轮换），主链路零远程调用
- [ ] 3.2 实现日期合法性校验，复用同一 `OPEN_DAYS`（`now()+1 .. now()+OPEN_DAYS`）
- [ ] 3.3 实现 `POST /api/v1/tickets/grab`：鉴权 → 日期校验 → 调用分段扣减 Lua → 可靠入队 → 返回"处理中"+ 凭据
- [ ] 3.4 实现可靠入队（方案 A）：扣减成功后写 Redis Stream 一条 pending（含 messageId/userId/date/segId）；入队不可靠则回补库存 + 移除限购标记
- [ ] 3.5 实现 `GET /api/v1/tickets/status`：按凭据返回 处理中/成功(含票据)/失败(含退款状态)
- [ ] 3.6 接入防刷限流：单 userId、单 IP 频率限制 + 峰值可启用验证码
- [ ] 3.7 单元/接口测试：有效/无效令牌、超窗口日期、售罄、入队失败回补、限流拦截

## 4. 访客系统对接（visitor-system-integration）

- [ ] 4.1 实现 Stream 消费者：在单个 MySQL 事务内 `INSERT ticket_order(RESERVING)` + `INSERT outbox_message(PENDING)`，含幂等检查（messageId 已存在则跳过）
- [ ] 4.2 定义并冻结预约请求消息契约（messageId/userId/visitDate/externalOrderNo/ticketType/timestamp）
- [ ] 4.3 实现 Outbox 异步发送任务：扫描 PENDING → 发送 `visitor.reserve.*` → 标记 SENT，失败保留待重试
- [ ] 4.4 实现回执监听 `ticket.reserve.reply.*`：成功→SUCCESS(记 visitorId)+通知；失败→重试/退款；含幂等保护（非 RESERVING 忽略）
- [ ] 4.5 记录消息收发审计到 `ticket_reserve_log`
- [ ] 4.6 单元测试：订单与 outbox 同事务、投递失败重试、重复回执被忽略、状态机流转正确

## 5. 补偿、对账与降级（reserve-reconciliation）

- [ ] 5.1 实现定时补偿任务：扫描超时 RESERVING → 未超上限重发（重置 outbox 为 PENDING）；超上限进入退款（受降级开关约束）
- [ ] 5.2 实现访客系统健康探针与全局熔断状态（NORMAL/DEGRADED），阈值配置化
- [ ] 5.3 退款分支加降级守卫：DEGRADED 下恒定 RESERVING + 只重发 + 短信"确认中"，恢复后自动追平
- [ ] 5.4 实现每日三源对账脚本：Redis 扣减量 == DB 订单数、DB 订单数 >= 访客预约成功数，不一致告警 + 差异报表
- [ ] 5.5 接入监控告警：接口 RT/错误率、RESERVING 堆积、库存负数、回执延迟、预约失败率
- [ ] 5.6 单元测试：降级期不退款、恢复后追平、退款策略无冲突、对账一致/不一致分支

## 6. 集成验证与联调

- [ ] 6.1 契约冻结确认（阻塞项）：与访客系统敲定 JWT 验签、预约/回执契约、messageId 幂等（或提供查预约状态接口作退路）
- [ ] 6.2 端到端联调：抢票→扣减→订单→outbox→访客预约→回执→状态查询 全链路打通
- [ ] 6.3 复用 miaosha 压测框架：单接口压测（50/200/500/1000 VUs）+ 超卖验证（超并发抢少量票，订单数 ≤ 配额）
- [ ] 6.4 灰度：冷门日期 200 张小流量，监控全指标；再按阶段全量
- [ ] 6.5 故障演练：MQ/访客系统/消费者宕机场景验证补偿与降级兜底
