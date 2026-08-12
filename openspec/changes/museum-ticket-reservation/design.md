## Context

见 proposal.md - Why。本设计在已有 miaosha（Spring Boot 2.2.5 / Java 8 / Redis / RabbitMQ / MySQL）的基础上，以**独立新模块**实现博物馆抢票，不改动现有三套抢购接口与表。设计需解决方案评审中发现的 6 个必改问题，并把它们落到具体技术选型。约束：抢票主链路 RT < 50ms、不超卖、不丢单、与外部访客系统仅通过 MQ 异步对接。

## Goals / Non-Goals

**Goals:**
- 抢票主链路零业务 DB 访问、零对访客系统的同步远程调用。
- 库存扣减在瞬时高峰下无单热点瓶颈，且严格防超卖。
- 抢票→订单→访客预约全链路可靠消息化，消除任何"扣了却丢"的竞态。
- 退款/不退款策略在任一时刻由唯一状态决定，无逻辑冲突。
- 新模块与现有 miaosha 代码物理隔离（独立包、独立表、独立 MQ 资源）。

**Non-Goals:**
- 不做支付与真实资金退款（"退款"= 释放票 + 通知）。
- 不做选座/选场次。
- 不重构现有 miaosha 抢购逻辑。
- 不实现访客系统内部逻辑（仅定义契约）。

## Decisions

### D1 分段库存（对应热点 key 问题）
- **选择**：单日库存拆为可配置 N 段（默认 8~10），key 形如 `museum:stock:{date}:seg{n}`；用户按 `userId % N` 稳定映射到某段扣减，段内售罄则顺时针探测其余段，全段无货才判售罄。回补时消息中携带 `segId`，精确 `incr` 回原段。
- **理由**：单 key `stock:{date}` 在 Redis Cluster 中只落一个分片，加从库也不分担写；分段把写压力线性打散到多分片。2000 张/天量级下 N=8~10 足够，段数配置化便于压测调优。
- **备选**：① 单 key（否，热点瓶颈）；② 本地批量预取库存（否，回补与对账复杂、易在实例伸缩时丢票）。
- 限购标记 `museum:user:grabbed:{date}`（Set）与扣减在同一 Lua 脚本内原子完成（sismember→段扣减→sadd），7 天过期。

### D2 本地 JWT 验签（对应同步鉴权问题）
- **选择**：访客系统登录期签发 JWT（RS256），我方仅缓存其公钥（JWKS endpoint，定时轮换）；抢票期纯本地验签，0 网络调用。
- **理由**：高峰期同步调访客系统验 token 会吃光 50ms 预算并打挂对方。
- **备选**：本地缓存 token 映射（否，仍需首验远程 + 缓存一致性问题）。
- **契约冻结项**：签名算法 RS256、JWKS 地址与轮换周期、token 有效期与刷新。

### D3 接口 Outbox 化（对应"裸露的那一跳"）
- **选择（方案 A）**：抢票接口只做 Redis 扣减，成功后写入 Redis Stream 一条 pending（携带 messageId、userId、date、segId），立即返回；由独立消费者从 Stream 消费，在**一个 MySQL 事务内** `INSERT ticket_order(RESERVING)` + `INSERT outbox_message(PENDING)`。Stream 具备 ACK + PEL 重投，天然可靠。
- **理由**：原设计接口→订单用裸 `rabbitTemplate.send`，"broker 收到但 confirm 超时→接口误判失败→incr 回补→消费者仍处理"会造成超卖。走 Stream 把这一跳纳入可靠模型，与全链路一致。
- **备选（方案 B）**：保留 MQ + publisher-confirm，超时不立即回补，改由查证任务确认真失败再 incr。作为不引入 Stream 时的退路。
- **原则**：MQ/投递超时 ≠ 失败，严禁凭超时立即回补。
- 访客系统方向沿用经典 Outbox：`outbox_message` 表 + 定时发送任务（PENDING→SENT），保证"订单在→消息一定在"。

### D4 退款熔断降级开关（对应退款策略矛盾）
- **选择**：引入访客系统健康探针驱动的全局状态 `NORMAL / DEGRADED`；补偿任务退款分支加守卫 `if (!breaker.isDegraded())`。DEGRADED 下订单恒定 RESERVING、只重发不退款、短信"预约确认中"；恢复后自动切回并追平。
- **理由**：原方案补偿 1 分钟/次、3 次即退（≈3 分钟），与 §8.5"访客系统挂 >1 小时不退款"直接冲突；熔断开关让退款行为在任一时刻唯一由当前模式决定。
- **触发条件（配置化）**：回执成功率 < X% 或连续超时 > Y 分钟置 DEGRADED；持续健康 Z 分钟恢复 NORMAL。

### D5 统一开放窗口（对应 off-by-one）
- **选择**：放票与校验共用同一常量 `OPEN_DAYS`，均从 `now()` 起算：放票 `for i in 1..OPEN_DAYS: now()+i`；校验 `now()+1 <= visitDate <= now()+OPEN_DAYS`。删除原 `tomorrow` 中间变量（off-by-one 根源）。
- **理由**：原 `tomorrow.plusDays(1..7)` 实际放 day+2..day+8，与校验 day+1..day+7 错位，导致明天无库存、第 8 天有货却不可抢。窗口单点推导杜绝再次漂移。

### D6 幂等契约硬约束（对应外部幂等假设）
- **选择**：补偿重发依赖访客系统对 `messageId` 幂等，将其从"假设"升级为**书面契约**并作为联调前置阻塞项；额外携带 `externalOrderNo` 作业务幂等键。若对方无法保证幂等，退路是提供同步"查预约状态"接口，补偿重发前先查后建。
- **理由**：重发若非幂等会造成重复预约（重复占用访客名额）。
- 我方回执消费侧已幂等（订单非 RESERVING 直接忽略），防 MQ 重试导致状态回退。

### 数据模型
- 新表：`ticket_quota`（日期配额）、`ticket_order`（PK=messageId，状态机 RESERVING/SUCCESS/REFUNDING/REFUNDED，含 segId、visitorId、retryCount）、`outbox_message`（PENDING/SENT/REPLIED/FAILED + idx_status_retry）、`ticket_reserve_log`（消息收发审计）。均为独立 DDL，不动 miaosha 现有表。
- MQ 资源：`ticket.order.*`（接口→订单，若走方案 B）、`visitor.reserve.*`（我方→访客）、`ticket.reserve.reply.*`（访客→我方），各配死信队列，持久化 + 手动 ACK。

## Risks / Trade-offs

- [分段库存增加回补/对账复杂度] → segId 全程随消息透传，回原段回补；对账按段汇总后与配额比对。
- [方案 A 依赖 Redis 持久化（AOF）] → Redis 开 AOF + 集群主从；若不接受，退到方案 B（MQ + confirm + 查证）。
- [访客系统不幂等则重发致重复预约] → D6 契约冻结 + externalOrderNo 业务幂等 + 查后建退路，作为联调阻塞项。
- [熔断阈值配置不当致误判降级或过晚降级] → 阈值配置化 + 灰度期结合监控调参。
- [新模块与 miaosha 共用 Redis/RabbitMQ] → 独立 key 前缀 `museum:` 与独立 exchange/queue 命名空间，避免资源冲突。
- [段数 N 与实例数不匹配导致分布不均] → N 配置化，压测阶段按热点分布调整。

## Migration Plan

- 独立 DDL 上线新表；新模块以独立包发布，开关默认关闭。
- 按 proposal 的三块能力分批灰度：先分段库存 + 抢票（冷门日期 200 张）→ 再接访客系统联调 → 最后补偿/对账/熔断全量。
- 回滚：新模块开关关闭即可下线抢票入口，不影响现有 miaosha；新表保留不删。

## Open Questions

- 分段数 N 的最终取值待压测确定（不影响 specs 与任务拆分，可后定）。
- 通知渠道（短信/站内信）具体服务商对接方式待运营确认（不影响主流程设计）。
