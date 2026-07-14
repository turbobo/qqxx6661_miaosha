

用 Redis+Lua 脚本实现原子性操作，适合多实例部署的系统（如秒杀接口）。
Lua 脚本（限制用户每秒最多 3 次请求）：

-- 限流key：user:limit:{用户ID}
local key = KEYS[1]
-- 限流次数
local limit = tonumber(ARGV[1])
-- 限流时间窗口（秒）
local window = tonumber(ARGV[2])

-- 当前请求次数
local current = tonumber(redis.call('get', key) or "0")
if current >= limit then
    return 0  -- 超出限制
end

-- 次数+1，设置过期时间
redis.call('incr', key)
redis.call('expire', key, window)
return 1  -- 允许请求

## 页面入口

- `purchase-version-comparison.html`：抢票版本方案效果对比页，对比 V1 悲观锁、V1 乐观锁、V2 异步 MQ、V3 实验链路的核心流程、效果指标、瓶颈与适用阶段，并提供单版本接口实测面板。
- `museum-ticket-purchase.html`：面向微信公众号最终抢票方案的完整移动端页面，覆盖票务列表、场次选择、抢票确认、获取 verifyHash、异步提交、排队轮询、结果展示、预约列表、订单详情和取消预约。

## 最终后端方案

`museum-ticket-purchase.html` 的最终抢票提交接口为 `POST /api/tickets/v3/purchase`，查询结果继续复用 `GET /api/tickets/v2/purchaseResult`。

### 接口链路

1. 前端调用 `GET /api/tickets/getVerifyHash?userId={userId}&date={date}` 获取一次性抢票凭证。
2. 前端调用 `POST /api/tickets/v3/purchase` 提交预约，参数包含 `userId`、`date`、`verifyHash`、`sessionId`、`sessionName`、`visitorName`、`visitorPhone`。
3. 后端完成参数校验、场次/参观人字段校验、时间校验、库存校验、用户校验、限流校验和 `verifyHash` 校验；校验通过后立即删除 `verifyHash`，确保同一凭证只能提交一次。
4. 后端生成 `requestId`，写入 Redis 初始状态 `QUEUED`，并投递到最终版 RabbitMQ 队列 `v3.miaosha.final.purchase.queue`。
5. MQ 消费者异步执行乐观锁扣库存、生成票码、创建订单，并把 `PROCESSING`、`SUCCESS`、`FAILED`、`SOLD_OUT`、`DUPLICATE` 等状态写入 Redis。
6. 前端轮询 `GET /api/tickets/v2/purchaseResult`，后端优先读取 Redis 中的最终状态；成功时返回 `ticketCode` 和 `orderNo`。
7. 预约列表、订单详情和取消预约继续使用 `/v1/order/list`、`/v1/order/detail`、`/v1/cancel`。

### 数据与缓存

- `PurchaseRequest` 已扩展 `sessionId`、`sessionName`、`visitorName`、`visitorPhone`，用于承接页面的场次和参观人信息。
- 抢票结果缓存键：`miaosha:final:purchase:result:{requestId}`，保留 24 小时。
- 请求时间缓存键：`request_time:{requestId}`，用于兼容原有轮询超时判断。
- `verifyHash` 使用既有 `miaosha_v1_user_hash_{date}_{userId}`，最终接口会强校验 Redis 中的凭证。
- 订单仍写入 `ticket_order`，场次与参观人信息通过 `remark` 保存 JSON 备注，避免新增表结构。

### 并发与一致性

- 使用用户+日期维度的 Redis 锁避免同一用户同一天重复建单。
- 使用数据库乐观锁扣减库存，失败自动重试，避免超卖。
- 下单完成后删除票券缓存并写入用户购买状态，列表接口可展示 `userPurchased`。
- MQ 消费失败不重新入最终队列，失败原因会落 Redis，前端可拿到确定结果，避免无限重试造成重复扣减。
