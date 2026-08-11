# 悲观锁购票V2实现说明

## 概述

本文档描述了新实现的悲观锁购票方法 `purchaseTicketWithPessimisticLockV2`，该方法使用 `SELECT FOR UPDATE` 锁住票券记录，通过事务控制确保数据一致性，扣减库存后生成 `ticket_order` 订单。

## 核心特性

### 1. 悲观锁机制
- 使用 `SELECT FOR UPDATE` 锁住票券记录
- 防止并发情况下的数据不一致问题
- 确保库存扣减的原子性

### 2. 事务控制
- 使用 `@Transactional` 注解确保事务完整性
- 传播级别：`Propagation.REQUIRED`
- 异常回滚：`rollbackFor = Exception.class`

### 3. 完整的业务流程
- 参数验证
- 抢购时间验证
- 用户重复购买检查
- 库存检查和扣减
- 票券编码生成
- 订单编号生成
- `ticket_order` 订单创建
- 缓存更新

## 方法签名

```java
@Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
public ApiResponse<PurchaseRecord> purchaseTicketWithPessimisticLockV2(PurchaseRequest request) throws Exception
```

## 业务流程详解

### 1. 参数验证
```java
if (request == null || request.getUserId() == null || request.getDate() == null) {
    return ApiResponse.error("请求参数不能为空");
}
```

### 2. 抢购时间验证
```java
validatePurchaseTime(purchaseDate);
```

### 3. 重复购买检查
```java
if (hasPurchased(userId, purchaseDate)) {
    return ApiResponse.error("用户已购买该日期的票券");
}
```

### 4. 悲观锁查询票券记录
```java
TicketEntity ticketEntity = ticketEntityMapper.selectByDateForUpdate(purchaseDate);
if (ticketEntity == null) {
    return ApiResponse.error("票券不存在");
}
```

### 5. 库存检查
```java
if (ticketEntity.getRemainingCount() <= 0) {
    return ApiResponse.error("票券已售罄");
}
```

### 6. 库存扣减
```java
int originalRemaining = ticketEntity.getRemainingCount();
int originalSold = ticketEntity.getSoldCount();

ticketEntity.setRemainingCount(originalRemaining - 1);
ticketEntity.setSoldCount(originalSold + 1);
ticketEntity.setVersion(ticketEntity.getVersion() + 1);
ticketEntity.setUpdateTime(new Date());

int updateResult = ticketEntityMapper.updateByPrimaryKey(ticketEntity);
if (updateResult <= 0) {
    throw new RuntimeException("库存扣减失败");
}
```

### 7. 票券编码生成
```java
String ticketCode = generateTicketCode(userId.toString(), purchaseDate);
```

### 8. 订单编号生成
```java
String orderNo = generateOrderNo(userId, purchaseDate);
// 格式：TB + 时间戳 + 用户ID后4位 + 随机数
```

### 9. 创建ticket_order订单
```java
TicketOrder ticketOrder = new TicketOrder();
ticketOrder.setOrderNo(orderNo);
ticketOrder.setUserId(userId);
ticketOrder.setTicketId(ticketEntity.getId());
ticketOrder.setTicketCode(ticketCode);
ticketOrder.setTicketDate(purchaseDate);
ticketOrder.setStatus(1); // 待支付
ticketOrder.setAmount(0L); // 免费票券，金额为0
ticketOrder.setCreateTime(new Date());
ticketOrder.setUpdateTime(new Date());
ticketOrder.setRemark("悲观锁购票生成");

int insertResult = ticketOrderMapper.insert(ticketOrder);
if (insertResult <= 0) {
    throw new RuntimeException("订单创建失败");
}
```

### 10. 缓存更新
```java
// 删除票券缓存
ticketCacheManager.deleteTicket(purchaseDate);

// 添加购买记录到缓存
PurchaseRecord purchaseRecord = new PurchaseRecord(userId, LocalDate.parse(purchaseDate), ticketCode);
ticketCacheManager.addPurchaseRecord(userId, purchaseDate, purchaseRecord);
```

## 接口调用

### REST API
```
POST /api/tickets/v3/purchase
```

### 请求参数
```json
{
    "userId": 1001,
    "date": "2025-01-20",
    "verifyHash": "abc123"
}
```

### 响应示例
```json
{
    "code": 200,
    "message": "success",
    "data": {
        "userId": 1001,
        "date": "2025-01-20",
        "ticketCode": "TC100120250120001"
    }
}
```

## 数据库表结构

### ticket_order表
```sql
CREATE TABLE ticket_order (
    id INT PRIMARY KEY AUTO_INCREMENT,
    order_no VARCHAR(50) NOT NULL UNIQUE COMMENT '订单编号',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    ticket_id INT NOT NULL COMMENT '票券ID',
    ticket_code VARCHAR(50) NOT NULL COMMENT '票券编码',
    ticket_date VARCHAR(10) NOT NULL COMMENT '票券日期',
    status INT DEFAULT 1 COMMENT '订单状态：1-待支付，2-已支付，3-已取消，4-已过期',
    amount BIGINT DEFAULT 0 COMMENT '订单金额（分）',
    pay_time TIMESTAMP NULL COMMENT '支付时间',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    remark VARCHAR(255) COMMENT '备注',
    INDEX idx_user_date (user_id, ticket_date),
    INDEX idx_order_no (order_no),
    INDEX idx_ticket_code (ticket_code)
) COMMENT '票券订单表';
```

## 测试用例

### 1. 单个用户购票测试
```java
@Test
public void testSingleUserPurchase() {
    PurchaseRequest request = new PurchaseRequest();
    request.setUserId(1001L);
    request.setDate("2025-01-20");
    
    ApiResponse<PurchaseRecord> response = ticketService.purchaseTicketWithPessimisticLockV2(request);
    
    if (response.getCode() == 200) {
        LOGGER.info("购票成功！票券编码: {}", response.getData().getTicketCode());
    } else {
        LOGGER.warn("购票失败：{}", response.getMessage());
    }
}
```

### 2. 并发购票测试
```java
@Test
public void testConcurrentPurchase() throws InterruptedException {
    int threadCount = 10;
    String purchaseDate = "2025-01-20";
    
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    CountDownLatch latch = new CountDownLatch(threadCount);
    AtomicInteger successCount = new AtomicInteger(0);
    AtomicInteger failCount = new AtomicInteger(0);
    
    // 并发执行购票操作
    // 悲观锁应该能正确处理并发，确保库存准确性
}
```

### 3. 重复购票测试
```java
@Test
public void testDuplicatePurchase() {
    // 测试同一用户同一日期的重复购票
    // 应该正确失败
}
```

## 优势特点

### 1. 数据一致性
- 悲观锁确保并发安全
- 事务控制保证原子性
- 库存扣减和订单创建在同一事务中

### 2. 性能优化
- 减少数据库往返次数
- 缓存更新策略
- 批量操作优化

### 3. 可维护性
- 清晰的业务流程
- 完善的日志记录
- 统一的异常处理

### 4. 扩展性
- 支持多种票券类型
- 可配置的限流策略
- 灵活的缓存策略

## 注意事项

### 1. 死锁预防
- 避免长时间持有锁
- 合理的事务边界
- 异常情况下的锁释放

### 2. 性能考虑
- 锁的粒度控制
- 事务超时设置
- 并发量控制

### 3. 监控告警
- 锁等待时间监控
- 事务执行时间监控
- 异常情况告警

## 总结

`purchaseTicketWithPessimisticLockV2` 方法通过悲观锁机制和事务控制，实现了高并发场景下的票券购买功能。该方法确保了数据一致性，提供了完整的业务流程，并具有良好的性能和可维护性。

该方法特别适用于：
- 高并发秒杀场景
- 对数据一致性要求较高的业务
- 需要防止超卖的场景
- 需要完整订单记录的业务
