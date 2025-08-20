# 悲观锁票券更新接口说明

## 概述

本接口使用悲观锁（Pessimistic Lock）来批量修改3天的票券数量，确保管理员操作不会影响用户抢票的并发情况。

## 核心特性

### 🔒 **悲观锁保护**
- 使用 `SELECT ... FOR UPDATE` 获取行级锁
- 防止并发修改同一张票券
- 确保数据一致性

### 🚀 **并发安全**
- 管理员操作与用户抢票完全隔离
- 使用独立的事务处理
- 不影响现有的抢票逻辑

### 📊 **批量操作**
- 支持一次性修改多天票券
- 原子性操作，要么全部成功，要么全部回滚
- 详细的执行结果反馈

## API 接口

### 接口地址
```
POST /api/tickets/admin/updateTicketsWithPessimistic
```

### 请求参数
```json
{
    "adminId": "admin",
    "ticketUpdates": [
        {
            "date": "2025-08-11",
            "name": "今日票券",
            "totalCount": 100,
            "remainingCount": 100
        },
        {
            "date": "2025-08-12",
            "name": "明日票券",
            "totalCount": 150,
            "remainingCount": 150
        },
        {
            "date": "2025-08-13",
            "name": "后日票券",
            "totalCount": 200,
            "remainingCount": 200
        }
    ]
}
```

### 响应格式
```json
{
    "success": true,
    "data": {
        "status": "SUCCESS",
        "message": "批量修改完成，成功: 3, 失败: 0",
        "totalCount": 3,
        "successCount": 3,
        "failCount": 0,
        "updateResults": [
            {
                "date": "2025-08-11",
                "status": "UPDATED",
                "message": "票券更新成功",
                "oldTotalCount": 80,
                "newTotalCount": 100,
                "oldRemainingCount": 30,
                "newRemainingCount": 100
            }
        ],
        "timestamp": 1640995200000
    }
}
```

## 技术实现

### 1. 悲观锁机制
```java
// 使用 FOR UPDATE 获取行级锁
TicketEntity ticketEntity = ticketEntityMapper.selectByDateForUpdate(update.getDate());
```

### 2. 事务管理
```java
@Transactional
public Map<String, Object> updateTicketsWithPessimistic(List<TicketUpdate> ticketUpdates)
```

### 3. 缓存同步
```java
// 更新Redis缓存，确保数据一致性
ticketCacheManager.deleteTicket(update.getDate());
```

## 并发安全性分析

### 🛡️ **用户抢票不受影响**

1. **独立的锁机制**
   - 用户抢票使用乐观锁（版本控制）
   - 管理员修改使用悲观锁（行级锁）
   - 两种锁机制互不干扰

2. **事务隔离**
   - 管理员操作在独立事务中执行
   - 用户抢票在另一个事务中执行
   - 事务之间完全隔离

3. **锁的粒度**
   - 悲观锁只锁定被修改的票券行
   - 其他票券仍然可以正常抢购
   - 锁的持有时间很短

### 📈 **性能优化**

1. **批量处理**
   - 一次请求处理多天票券
   - 减少网络往返次数
   - 提高整体效率

2. **缓存更新**
   - 批量更新Redis缓存
   - 避免频繁的缓存操作
   - 保持缓存一致性

## 使用场景

### 🎯 **适用场景**
- 管理员批量调整票券数量
- 系统维护时更新票券信息
- 活动配置时修改票券参数

### ⚠️ **注意事项**
- 建议在用户抢票低峰期执行
- 避免频繁修改同一张票券
- 确保管理员权限验证

## 测试方法

### 1. 启动应用
```bash
mvn spring-boot:run
```

### 2. 访问测试页面
```
http://localhost:8081/admin-ticket-update.html
```

### 3. 测试并发场景
```bash
# 同时启动多个用户抢票请求
curl -X POST http://localhost:8081/api/tickets/purchase \
  -H "Content-Type: application/json" \
  -d '{"userId":"user1","date":"2025-08-11"}'

# 同时执行管理员修改
curl -X POST http://localhost:8081/api/tickets/admin/updateTicketsWithPessimistic \
  -H "Content-Type: application/json" \
  -d '{"adminId":"admin","ticketUpdates":[...]}'
```

## 错误处理

### 🔍 **常见错误**

1. **权限不足**
   ```
   "权限不足，需要管理员权限"
   ```

2. **参数验证失败**
   ```
   "请求参数不能为空"
   ```

3. **数据库操作失败**
   ```
   "票券更新失败"
   ```

### 🛠️ **故障排除**

1. **检查管理员ID**
   - 确保 `adminId` 为 "admin"

2. **验证请求参数**
   - 检查日期格式（YYYY-MM-DD）
   - 确保票数为正整数

3. **查看日志**
   - 检查应用启动日志
   - 查看数据库连接状态

## 监控和告警

### 📊 **关键指标**
- 批量更新成功率
- 平均执行时间
- 并发冲突次数

### 🚨 **告警规则**
- 更新失败率 > 10%
- 执行时间 > 5秒
- 数据库连接异常

## 总结

这个悲观锁接口提供了安全、高效的票券批量修改功能：

✅ **安全性**：使用悲观锁确保数据一致性  
✅ **并发性**：不影响用户抢票的正常流程  
✅ **可靠性**：事务保证操作的原子性  
✅ **易用性**：提供友好的Web界面和API接口  

通过合理使用这个接口，管理员可以在不影响用户体验的情况下，灵活调整票券配置。
