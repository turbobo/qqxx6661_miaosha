# 悲观锁购票功能说明

## 功能概述

本系统实现了基于悲观锁的票券购买功能，通过数据库级别的行锁（SELECT ... FOR UPDATE）来确保在高并发场景下的数据一致性和库存准确性。

## 核心特性

### 1. 悲观锁机制
- 使用 `SELECT ... FOR UPDATE` 语句获取行级锁
- 在事务期间锁定票券记录，防止其他事务同时修改
- 确保库存扣减的原子性和一致性

### 2. 事务控制
- 主方法使用 `@Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)`
- 库存扣减使用独立事务 `@Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRES_NEW)`
- 订单创建使用独立事务 `@Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRES_NEW)`

### 3. 完整的购票流程
1. 参数校验（空值、限流、合法性）
2. 悲观锁扣减库存
3. 生成票券编码
4. 创建购票记录
5. 创建订单
6. 更新缓存
7. 返回结果

## 使用方法

### 1. 接口调用

```java
@Resource
private TicketService ticketService;

// 创建购票请求
PurchaseRequest request = new PurchaseRequest();
request.setUserId(1L);
request.setDate("2024-01-15");
request.setVerifyHash("verification_hash");

// 调用悲观锁购票方法
ApiResponse<PurchaseRecord> response = ticketService.purchaseTicketWithPessimisticLock(request);

if (response.getCode() == 200) {
    PurchaseRecord record = response.getData();
    // 购票成功处理
} else {
    // 购票失败处理
}
```

### 2. 方法签名

```java
@Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
public ApiResponse<PurchaseRecord> purchaseTicketWithPessimisticLock(PurchaseRequest request) throws Exception
```

## 技术实现

### 1. 库存扣减方法

```java
@Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRES_NEW)
public TicketEntity deductStockWithPessimisticLock(String date) throws Exception
```

- 使用 `selectByDateForUpdate` 获取悲观锁
- 检查库存充足性
- 原子性更新库存和已售数量
- 版本号递增

### 2. 订单创建方法

```java
@Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRES_NEW)
public void createOrderWithTransaction(PurchaseRequest request, Integer ticketId, String ticketCode) throws Exception
```

- 调用订单服务创建订单
- 使用独立事务确保订单创建的原子性
- 异常时自动回滚

### 3. 购票记录管理

```java
private TicketPurchaseRecord createPurchaseRecord(PurchaseRequest request, String ticketCode)
private void updatePurchaseRecordAfterOrderCreation(TicketPurchaseRecord purchaseRecord, Integer ticketId)
```

- 创建完整的购票记录
- 支持后续的订单ID和票券ID更新

## 数据库要求

### 1. 票券表结构

确保 `ticket_entity` 表包含以下字段：
- `id`: 主键
- `date`: 票券日期
- `total_count`: 总票数
- `remaining_count`: 剩余票数
- `sold_count`: 已售票数
- `version`: 版本号（用于乐观锁）

### 2. 购票记录表结构

确保 `ticket_purchase_record` 表包含以下字段：
- `id`: 主键
- `user_id`: 用户ID
- `ticket_date`: 票券日期
- `ticket_code`: 票券编码
- `order_id`: 订单ID
- `ticket_id`: 票券ID
- `status`: 状态
- `create_time`: 创建时间
- `update_time`: 更新时间

### 3. 悲观锁查询

确保 `TicketEntityMapper` 包含以下方法：

```xml
<select id="selectByDateForUpdate" resultType="TicketEntity">
    SELECT * FROM ticket_entity WHERE date = #{date} FOR UPDATE
</select>
```

## 性能考虑

### 1. 锁粒度
- 使用行级锁，最小化锁范围
- 只锁定特定日期的票券记录

### 2. 事务时长
- 库存扣减使用独立短事务
- 订单创建使用独立事务
- 主方法事务时间控制在合理范围内

### 3. 并发处理
- 支持高并发购票场景
- 通过悲观锁防止超卖
- 异常时自动回滚，保证数据一致性

## 异常处理

### 1. 业务异常
- `TICKET_NOT_FOUND`: 票券不存在
- `STOCK_NOT_ENOUGH`: 库存不足
- `STOCK_UPDATE_FAILED`: 库存更新失败
- `ORDER_CREATE_FAILED`: 订单创建失败

### 2. 系统异常
- 数据库连接异常
- 事务超时异常
- 缓存更新异常

### 3. 回滚机制
- 任何异常发生时自动回滚事务
- 确保数据一致性
- 支持手动重试

## 测试建议

### 1. 单元测试
- 测试正常购票流程
- 测试异常情况处理
- 测试事务回滚机制

### 2. 并发测试
- 使用JMeter等工具进行压力测试
- 验证悲观锁的有效性
- 测试高并发下的性能表现

### 3. 集成测试
- 测试完整的购票流程
- 测试与订单服务的集成
- 测试缓存更新机制

## 注意事项

1. **死锁预防**: 确保锁的获取顺序一致，避免死锁
2. **超时设置**: 合理设置数据库锁超时时间
3. **监控告警**: 监控锁等待时间和事务执行时间
4. **性能调优**: 根据实际业务场景调整事务隔离级别
5. **备份策略**: 定期备份票券和购票记录数据

## 扩展建议

1. **异步处理**: 考虑将订单创建改为异步处理
2. **缓存优化**: 优化Redis缓存策略，减少数据库压力
3. **分库分表**: 根据业务量考虑分库分表方案
4. **监控完善**: 添加更详细的性能监控和告警
