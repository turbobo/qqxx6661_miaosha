## Why

现有 miaosha 系统已验证"Redis 预扣减 + MQ 异步"是高并发抢购的最优解（实测 QPS 3944，远超悲观锁 300 / 乐观锁成功率 1.1%）。现需在此经验之上引入**博物馆预约抢票**：每日限量放票（默认 2000，可配置）、周末瞬时高峰，抢票成功后需**异步对接已有的访客系统**完成预约，且必须做到**不超卖、不丢单**。

直接改造现有三套抢购模式风险高，因此本次以**新建独立模块/类**（greenfield）实现，与现有 miaosha 抢购逻辑隔离，互不影响。同时在方案评审中发现的 6 个必改问题（单热点 key、同步鉴权、裸露 MQ 跳、退款策略矛盾、放票窗口 off-by-one、外部幂等假设）在本次一并收口。

## What Changes

- 新增**按日期分库存**模型，并用**分段库存（segmented stock）**打散单热点 key，解决单节点写压力瓶颈。
- 新增**每日放票定时任务**，为"今天+1 .. 今天+N"统一窗口预放票，修复放票窗口与校验窗口不一致的 off-by-one。
- 新增**抢票主流程接口**（`POST /api/v1/tickets/grab` + 状态查询），接口只做校验 + Redis 原子扣减 + 可靠入队，目标 RT < 50ms。
- 鉴权改为**纯本地 JWT 验签**（访客系统颁发、我方缓存公钥），主链路零远程调用。**BREAKING**（对接契约）：要求访客系统提供 JWKS/公钥与签名算法约定。
- 新增**访客系统对接**：Outbox 本地事务表 + 异步发送任务 + 双向 MQ 契约（预约请求 / 预约回执），实现可靠消息最终一致；接口→订单这一跳也纳入可靠模型，消除"扣了但入队丢"的竞态。
- 新增**补偿与对账**：定时补偿（防回执丢失）、**退款熔断降级开关**（统一"3 分钟退款"与"降级期不退款"的矛盾）、三数据源（Redis/DB/访客系统）每日对账与告警。
- 新增数据表：`ticket_quota`、`ticket_order`、`outbox_message`、`ticket_reserve_log`（新表，不改动现有 miaosha 表）。

## Capabilities

### New Capabilities
- `date-partitioned-stock`: 按访问日期维度的库存管理，包含分段库存抗热点、Lua 原子扣减/回补、每日放票定时初始化与配额配置。
- `ticket-grab`: 抢票主流程能力，包含本地 JWT 鉴权、日期合法校验、Redis 预扣减、可靠入队、抢票结果状态查询与防刷限流。
- `visitor-system-integration`: 与已有访客系统的异步对接能力，包含 Outbox 可靠消息、双向 MQ 消息契约、消费幂等与订单状态机流转。
- `reserve-reconciliation`: 一致性保障能力，包含定时补偿任务、退款熔断降级开关、三数据源对账与监控告警。

### Modified Capabilities
<!-- 无：本次为独立新模块，不修改现有 miaosha 抢购能力的 spec 级行为。 -->

## Impact

- **新增代码模块**：在 `miaosha-service` / `miaosha-web` 下新建博物馆抢票相关的 controller / service / receiver / task / config 类（独立包，不改现有抢购类）。
- **数据层**：`miaosha-dao` 新增 4 张表的实体与 Mapper；提供独立 DDL 脚本。
- **中间件**：复用现有 Redis（新增分段 key 空间）、RabbitMQ（新增 exchange/queue：`ticket.order.*`、`visitor.reserve.*`、`ticket.reserve.reply.*` + 死信队列）。
- **外部系统**：与访客系统需冻结三项契约——SSO/JWT 验签、预约消息契约、回执消息契约及其 `messageId` 幂等保证。
- **配置**：新增放票开放天数 `OPEN_DAYS`、每日配额、分段数、熔断阈值等可配置项。
- **不影响**：现有 miaosha 的悲观锁/乐观锁/异步三套抢购接口与表结构保持不变。
