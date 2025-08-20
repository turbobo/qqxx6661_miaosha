# 异步抢购功能实现说明

## 概述

本功能实现了基于消息队列的异步抢购系统，前端发送抢购请求后，系统将请求放入消息队列异步处理，前端通过轮询查询抢购结果。这种设计可以有效应对高并发场景，提升系统性能和用户体验。

## 架构设计

```
┌─────────────┐    ┌─────────────┐    ┌─────────────┐    ┌─────────────┐
│   前端页面   │───▶│   Controller │───▶│  消息队列   │───▶│  消息消费者  │
│             │    │             │    │             │    │             │
└─────────────┘    └─────────────┘    └─────────────┘    └─────────────┘
       │                   │                   │                   │
       │                   │                   │                   ▼
       │                   │                   │            ┌─────────────┐
       │                   │                   │            │  业务处理   │
       │                   │                   │            │  (基于V1)  │
       │                   │                   │            └─────────────┘
       │                   │                   │                   │
       │                   │                   │                   ▼
       │                   │                   │            ┌─────────────┐
       │                   │                   │            │  结果缓存   │
       │                   │                   │            │  (Redis)   │
       │                   │                   │            └─────────────┘
       │                   │                   │                   │
       │                   │                   │                   ▼
       │                   │                   │            ┌─────────────┐
       │                   │                   │            │  状态更新   │
       │                   │                   │            └─────────────┘
       │                   │                   │                   │
       │                   │                   │                   │
       ▼                   ▼                   ▼                   │
┌─────────────┐    ┌─────────────┐    ┌─────────────┐            │
│   轮询查询   │◀───│  结果查询   │◀───│  状态缓存   │◀───────────┘
│   (500ms)   │    │   接口     │    │             │
└─────────────┘    └─────────────┘    └─────────────┘
```

## 核心组件

### 1. 消息实体类

#### `PurchaseMessage` - 抢购消息实体
- **位置**: `miaosha-web/src/main/java/cn/monitor4all/miaoshaweb/model/PurchaseMessage.java`
- **功能**: 封装抢购请求信息，包含用户ID、票券日期、验证码等
- **特点**: 支持消息状态管理、重试机制、序列化传输

#### `PurchaseResult` - 抢购结果实体
- **位置**: `miaosha-web/src/main/java/cn/monitor4all/miaoshaweb/model/PurchaseResult.java`
- **功能**: 封装抢购处理结果，包含状态、结果信息、错误信息等
- **特点**: 支持多种状态查询、结果缓存、时间记录

### 2. 消息服务

#### `PurchaseMessageService` - 抢购消息服务接口
- **位置**: `miaosha-web/src/main/java/cn/monitor4all/miaoshaweb/service/PurchaseMessageService.java`
- **功能**: 定义抢购消息处理的核心方法
- **方法**:
  - `sendPurchaseMessage()` - 发送抢购消息
  - `getPurchaseResult()` - 查询抢购结果
  - `processPurchaseMessage()` - 处理抢购消息
  - `hasExistingOrder()` - 检查是否已有订单

#### `PurchaseMessageServiceImpl` - 抢购消息服务实现
- **位置**: `miaosha-web/src/main/java/cn/monitor4all/miaoshaweb/service/impl/PurchaseMessageServiceImpl.java`
- **功能**: 实现抢购消息的具体处理逻辑
- **特点**: 
  - 集成RabbitMQ消息队列
  - 使用Redis缓存结果
  - 支持内存缓存和持久化存储
  - 完善的异常处理和重试机制

### 3. 消息消费者

#### `PurchaseMessageReceiver` - 抢购消息消费者
- **位置**: `miaosha-web/src/main/java/cn/monitor4all/miaoshaweb/receiver/PurchaseMessageReceiver.java`
- **功能**: 监听消息队列，接收并处理抢购消息
- **特点**:
  - 支持普通队列和延迟队列
  - 自动消息确认和重试
  - 完善的日志记录

### 4. 控制器接口

#### `TicketController.purchaseTicketV2()` - 异步抢购接口
- **功能**: 接收前端抢购请求，发送消息到队列
- **返回**: 消息ID和轮询建议
- **特点**: 快速响应，不阻塞用户

#### `TicketController.getPurchaseResult()` - 结果查询接口
- **功能**: 根据消息ID查询抢购结果
- **用途**: 前端轮询调用
- **特点**: 轻量级查询，支持高并发

#### `TicketController.getPurchaseResultByUser()` - 用户结果查询接口
- **功能**: 查询用户指定日期的抢购结果
- **用途**: 检查是否已有订单
- **特点**: 支持订单去重检查

## 工作流程

### 1. 抢购请求流程

```
1. 前端发送抢购请求
   ↓
2. Controller接收请求，验证参数
   ↓
3. 检查用户是否已有订单（去重）
   ↓
4. 创建PurchaseMessage对象
   ↓
5. 发送消息到RabbitMQ队列
   ↓
6. 初始化结果缓存
   ↓
7. 返回消息ID和轮询建议
```

### 2. 消息处理流程

```
1. 消息消费者接收消息
   ↓
2. 解析消息内容
   ↓
3. 更新状态为"处理中"
   ↓
4. 检查是否已有订单
   ↓
5. 调用业务逻辑（基于purchaseTicketV1）
   ↓
6. 更新处理结果
   ↓
7. 保存结果到缓存
```

### 3. 结果查询流程

```
1. 前端轮询调用查询接口
   ↓
2. 根据消息ID查询结果
   ↓
3. 返回当前处理状态
   ↓
4. 前端根据状态决定是否继续轮询
   ↓
5. 处理完成后停止轮询
```

## 配置说明

### 1. 消息队列配置

```properties
# application-purchase.properties
purchase.message.exchange=purchase.exchange
purchase.message.routing.key=purchase
purchase.message.queue=purchase.queue
purchase.message.delayed.exchange=purchase.delayed.exchange
purchase.message.delayed.routing.key=purchase.delayed
purchase.message.delayed.queue=purchase.delayed.queue
```

### 2. 缓存配置

```properties
# 抢购结果缓存过期时间
purchase.message.result.expire.hours=24
```

### 3. RabbitMQ配置

```properties
# 连接配置
spring.rabbitmq.host=localhost
spring.rabbitmq.port=5672
spring.rabbitmq.username=guest
spring.rabbitmq.password=guest

# 监听器配置
spring.rabbitmq.listener.simple.retry.enabled=true
spring.rabbitmq.listener.simple.retry.max-attempts=3
spring.rabbitmq.listener.simple.concurrency=2
spring.rabbitmq.listener.simple.max-concurrency=5
```

## 前端集成

### 1. 演示页面

- **位置**: `miaosha-web/src/main/resources/async-purchase-demo.html`
- **功能**: 完整的异步抢购演示
- **特点**: 
  - 实时状态显示
  - 自动轮询查询
  - 详细的日志记录
  - 响应式设计

### 2. 前端调用示例

```javascript
// 发送抢购请求
fetch('/api/tickets/v2/purchase', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
        userId: 1001,
        date: '2025-01-15',
        verifyHash: 'test123'
    })
})
.then(response => response.json())
.then(data => {
    if (data.code === 200) {
        const messageId = data.data.messageId;
        // 开始轮询查询结果
        startPolling(messageId);
    }
});

// 轮询查询结果
function startPolling(messageId) {
    const interval = setInterval(() => {
        fetch(`/api/tickets/v2/purchase/result?messageId=${messageId}`)
        .then(response => response.json())
        .then(data => {
            if (data.data.status === 'SUCCESS' || data.data.status === 'FAILED') {
                clearInterval(interval);
                // 处理最终结果
            }
        });
    }, 500); // 500ms轮询间隔
}
```

## 性能优化

### 1. 消息队列优化

- **队列分区**: 支持多个队列分区，提高并发处理能力
- **消息持久化**: 重要消息持久化存储，防止丢失
- **死信队列**: 处理失败的消息，支持重试和告警

### 2. 缓存优化

- **多级缓存**: 内存缓存 + Redis缓存，提高查询性能
- **缓存预热**: 系统启动时预热热点数据
- **缓存更新**: 异步更新缓存，减少阻塞

### 3. 轮询优化

- **智能轮询**: 根据处理状态动态调整轮询间隔
- **批量查询**: 支持批量查询多个消息结果
- **长轮询**: 支持长轮询，减少无效请求

## 监控和告警

### 1. 业务监控

- **消息处理成功率**: 监控消息处理的成功/失败比例
- **处理延迟**: 监控从消息发送到处理完成的时间
- **队列积压**: 监控消息队列的积压情况

### 2. 系统监控

- **内存使用**: 监控缓存内存使用情况
- **CPU使用率**: 监控消息处理的CPU消耗
- **网络连接**: 监控RabbitMQ连接状态

### 3. 告警机制

- **处理失败告警**: 消息处理失败时及时告警
- **队列积压告警**: 队列积压超过阈值时告警
- **系统异常告警**: 系统异常时及时通知

## 部署建议

### 1. 开发环境

- 使用本地RabbitMQ服务
- 简化配置，快速验证功能
- 启用详细日志，便于调试

### 2. 测试环境

- 模拟生产环境配置
- 进行压力测试和性能测试
- 验证异常场景的处理

### 3. 生产环境

- 使用集群化的RabbitMQ
- 配置监控和告警
- 定期清理过期数据
- 备份重要配置和数据

## 故障排查

### 1. 常见问题

- **消息发送失败**: 检查RabbitMQ连接状态
- **消息处理超时**: 检查业务逻辑执行时间
- **结果查询失败**: 检查缓存和数据库状态

### 2. 排查步骤

1. 检查应用日志，定位错误位置
2. 检查RabbitMQ管理界面，查看队列状态
3. 检查Redis连接和缓存数据
4. 检查数据库连接和业务数据

### 3. 恢复策略

- **消息重试**: 失败消息自动重试
- **手动重发**: 支持手动重新发送消息
- **数据修复**: 支持数据不一致时的修复

## 总结

异步抢购功能通过消息队列实现了请求的异步处理，有效提升了系统的并发处理能力和用户体验。主要优势包括：

1. **高并发支持**: 消息队列可以缓冲大量请求，避免系统过载
2. **快速响应**: 前端请求快速响应，不阻塞用户
3. **可靠性**: 消息持久化和重试机制保证业务可靠性
4. **可扩展性**: 支持水平扩展，增加消费者数量
5. **监控友好**: 完善的监控和告警机制

该功能为秒杀系统提供了稳定可靠的技术基础，可以有效应对高并发抢购场景。
