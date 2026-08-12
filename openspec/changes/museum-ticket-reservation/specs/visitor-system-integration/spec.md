## Purpose

将抢票订单与已有访客系统通过异步消息可靠对接，保证抢票成功后最终一定完成访客预约，且在任意单点故障下不丢单、不重复预约。

## ADDED Requirements

### Requirement: 订单与待发消息的原子登记

系统 SHALL 在同一本地数据库事务内完成"创建订单"与"登记待发预约消息"，使得订单存在则预约消息必然待发，二者 MUST NOT 出现只有其一的情况。

#### Scenario: 订单与消息同生

- **WHEN** 处理一条抢票任务
- **THEN** 订单记录与对应的待发预约消息 SHALL 在同一事务中一起提交或一起回滚

### Requirement: 可靠消息投递

系统 SHALL 将待发预约消息异步、可靠地投递到访客系统，投递失败 SHALL 自动重试直至成功或进入失败处理，消息 MUST NOT 因单次投递失败而丢失。

#### Scenario: 投递失败自动重试

- **WHEN** 一条待发预约消息投递访客系统失败
- **THEN** 系统 SHALL 保留其待发状态并在后续周期重试投递

#### Scenario: 投递成功后标记

- **WHEN** 预约消息成功投递
- **THEN** 系统 SHALL 将该消息标记为已发送，避免重复投递

### Requirement: 预约消息契约

系统与访客系统之间的预约请求消息 SHALL 携带全局唯一的 `messageId`、用户标识、访问日期、外部订单号等约定字段。访客系统 MUST 以 `messageId` 作为幂等键，对同一 `messageId` 的重复请求 MUST NOT 创建重复预约。

#### Scenario: 重复请求不重复预约

- **WHEN** 访客系统收到 `messageId` 相同的预约请求多次
- **THEN** 访客系统 SHALL 仅创建一次预约，并对重复请求返回首次结果

### Requirement: 回执处理与状态流转

系统 SHALL 监听访客系统的预约回执并据此流转订单状态：成功则置为成功并记录访客系统单号；失败则进入重试或退款流程。订单状态机 SHALL 为 RESERVING → SUCCESS / REFUNDING → REFUNDED。

#### Scenario: 成功回执

- **WHEN** 收到预约成功回执
- **THEN** 系统 SHALL 将订单置为 SUCCESS、记录访客系统单号并通知用户

#### Scenario: 失败回执触发重试或退款

- **WHEN** 收到预约失败回执
- **THEN** 系统 SHALL 在未超重试上限时重发预约，超过上限时转入退款流程

### Requirement: 回执消费幂等

系统对访客系统回执 SHALL 幂等处理，重复回执 MUST NOT 导致订单状态回退或被重复处理。

#### Scenario: 重复回执被忽略

- **WHEN** 收到针对已处于终态或已处理订单的重复回执
- **THEN** 系统 SHALL 忽略该回执并保持当前状态不变

### Requirement: 消息可靠性保障

预约消息与回执 SHALL 使用持久化队列与持久化消息，并配置死信队列兜底。消费端 SHALL 采用手动确认，处理成功后方可确认。

#### Scenario: 消费失败不丢消息

- **WHEN** 消费端处理消息过程中失败
- **THEN** 消息 SHALL NOT 被确认，可被重投或进入死信队列，不发生静默丢失
