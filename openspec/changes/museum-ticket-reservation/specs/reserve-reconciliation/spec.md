## Purpose

通过定时补偿、退款熔断降级与多数据源对账，兜底保障抢票与访客预约之间的最终一致性，并在访客系统异常时保护用户体验。

## ADDED Requirements

### Requirement: 定时补偿

系统 SHALL 定时扫描长时间停留在 RESERVING 状态的订单，对已发送但迟迟无回执者重发预约请求。补偿 SHALL 有重试上限，超上限进入退款流程（受降级开关约束）。

#### Scenario: 无回执订单被重发

- **WHEN** 某订单处于 RESERVING 且超过配置时限仍无回执
- **THEN** 系统 SHALL 在未超重试上限时重发预约请求

#### Scenario: 超重试上限进入退款

- **WHEN** 某订单补偿重试次数超过上限且未处于降级模式
- **THEN** 系统 SHALL 将订单转入退款流程并回补库存、通知用户与告警

### Requirement: 退款熔断降级开关

系统 SHALL 提供访客系统健康状态驱动的降级开关。当访客系统被判定为不可用（降级模式）时，退款分支 MUST 被禁用：所有受影响订单保持 RESERVING 并持续补偿，MUST NOT 触发退款。访客系统恢复后 SHALL 自动切回正常模式并追平积压。

#### Scenario: 降级期不退款

- **WHEN** 系统处于降级模式且存在长时间未回执订单
- **THEN** 系统 SHALL 保持订单为 RESERVING、继续重发，MUST NOT 执行退款

#### Scenario: 恢复后自动追平

- **WHEN** 访客系统从不可用恢复
- **THEN** 系统 SHALL 自动切回正常模式并对积压订单继续正常流转

#### Scenario: 策略无冲突

- **WHEN** 正常模式与降级模式先后切换
- **THEN** 任一时刻退款行为 SHALL 唯一由当前模式决定，不存在正常退款逻辑与降级不退款逻辑同时生效的矛盾

### Requirement: 多数据源对账

系统 SHALL 每日对账 Redis 实际扣减量、数据库订单数、访客系统预约成功数三个数据源。三者关系 SHALL 满足：Redis 扣减量等于数据库订单数，数据库订单数不小于访客预约成功数（允许在途 RESERVING）。不一致 SHALL 触发告警并生成差异报表。

#### Scenario: 一致则通过

- **WHEN** 对账时三数据源满足约定关系
- **THEN** 对账 SHALL 通过，不产生告警

#### Scenario: 不一致则告警

- **WHEN** 对账时任一约定关系被打破
- **THEN** 系统 SHALL 触发告警并生成差异报表供人工介入

### Requirement: 监控与告警

系统 SHALL 对关键指标（抢票接口 RT/错误率、RESERVING 堆积、库存负数、回执延迟、预约失败率）进行监控，超阈值 SHALL 按严重程度触发相应告警。

#### Scenario: 库存出现负数立即告警

- **WHEN** 任一分段库存被监测到小于 0
- **THEN** 系统 SHALL 立即触发最高优先级告警

#### Scenario: 订单堆积告警

- **WHEN** RESERVING 订单在单位时间内堆积超过阈值
- **THEN** 系统 SHALL 触发告警
