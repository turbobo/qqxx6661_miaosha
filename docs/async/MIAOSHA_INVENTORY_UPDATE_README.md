# 秒杀过程中修改余票数量解决方案

本项目实现了两种在秒杀过程中修改余票数量的方案，满足不同场景下的需求。

## 方案概述

### 方案一：暂停秒杀活动后修改（悲观锁）
- **优势**：安全性高，数据一致性好，避免并发冲突
- **不足**：会中断秒杀活动，影响用户体验
- **适用场景**：对数据一致性要求极高，可以接受短暂中断的场景

### 方案二：基于乐观锁的无感知修改
- **优势**：不中断秒杀活动，用户体验好
- **不足**：可能存在重试开销，极端情况下数据可能不一致
- **适用场景**：对秒杀连续性要求高，可以接受轻微数据不一致的场景

## 系统架构

```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│   前端页面      │    │   后端服务       │    │   数据库        │
│                 │    │                 │    │                 │
│ - 用户抢购页面  │◄──►│ - TicketController│◄──►│ - ticket表      │
│ - 管理控制台    │    │ - MiaoshaStatus  │    │ - 乐观锁版本控制│
│                 │    │ - 乐观锁服务     │    │                 │
└─────────────────┘    └──────────────────┘    └─────────────────┘
```

## 核心组件

### 1. 秒杀活动状态管理服务
- **接口**：`MiaoshaStatusService`
- **实现**：`MiaoshaStatusServiceImpl`
- **功能**：控制秒杀活动的开启/暂停状态
- **存储**：Redis缓存

### 2. 乐观锁无感知修改服务
- **接口**：`TicketOptimisticUpdateService`
- **实现**：`TicketOptimisticUpdateServiceImpl`
- **功能**：使用乐观锁在秒杀过程中修改库存
- **特性**：自动重试机制，重试统计

### 3. 悲观锁修改服务
- **接口**：现有`TicketService.updateTicketsWithPessimistic`
- **功能**：使用悲观锁批量修改票券库存

## API接口说明

### 秒杀活动状态管理

#### 暂停秒杀活动
```http
POST /api/tickets/admin/pauseMiaosha
```
**响应示例**：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "status": "SUCCESS",
    "message": "秒杀活动已暂停",
    "timestamp": 1703123456789
  }
}
```

#### 恢复秒杀活动
```http
POST /api/tickets/admin/resumeMiaosha
```
**响应示例**：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "status": "SUCCESS",
    "message": "秒杀活动已恢复",
    "timestamp": 1703123456789
  }
}
```

#### 获取秒杀活动状态
```http
GET /api/tickets/miaosha/status
```
**响应示例**：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "paused": true,
    "status": "PAUSED",
    "pauseTime": 1703123456789,
    "operator": "admin",
    "reason": "库存维护"
  }
}
```

### 库存修改接口

#### 悲观锁修改（暂停秒杀后）
```http
POST /api/tickets/admin/updateTicketsWithPessimistic
```
**请求体**：
```json
{
  "adminId": "admin",
  "ticketUpdates": [
    {
      "date": "2024-01-15",
      "name": "今日票券",
      "totalCount": 100,
      "remainingCount": 80
    }
  ]
}
```

#### 乐观锁无感知修改
```http
POST /api/tickets/admin/updateTicketsWithOptimistic
```
**请求体**：同悲观锁修改

#### 获取乐观锁重试统计
```http
GET /api/tickets/admin/optimisticRetryStats
```
**响应示例**：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "totalRetryCount": 5,
    "totalUpdateCount": 10,
    "averageRetryCount": 0.5,
    "ticketRetryCounts": {
      "2024-01-15": 2,
      "2024-01-16": 3
    },
    "timestamp": 1703123456789
  }
}
```

## 使用方法

### 1. 启动系统
```bash
# 启动Redis
redis-server

# 启动后端服务
cd miaosha-service
mvn spring-boot:run

# 启动Web服务
cd miaosha-web
mvn spring-boot:run
```

### 2. 访问管理控制台
打开浏览器访问：`http://localhost:8081/admin-miaosha-control.html`

### 3. 使用方案一：暂停秒杀后修改

#### 步骤1：暂停秒杀活动
1. 在管理控制台点击"暂停秒杀活动"按钮
2. 系统会拒绝新的秒杀请求，显示"秒杀活动正在维护中"

#### 步骤2：修改库存
1. 选择"使用悲观锁修改"
2. 输入管理员ID（admin）
3. 配置票券信息（日期、名称、总票数、剩余票数）
4. 点击"悲观锁修改"按钮

#### 步骤3：恢复秒杀活动
1. 点击"恢复秒杀活动"按钮
2. 系统重新开放秒杀接口

### 4. 使用方案二：乐观锁无感知修改

#### 步骤1：直接修改库存
1. 选择"使用乐观锁修改"
2. 输入管理员ID（admin）
3. 配置票券信息
4. 点击"乐观锁修改"按钮
5. 系统自动处理并发冲突，无需暂停秒杀活动

#### 步骤2：查看重试统计
1. 修改完成后自动显示重试统计信息
2. 包括总重试次数、平均重试次数等

## 数据库设计

### ticket表结构
```sql
CREATE TABLE ticket (
    id INT PRIMARY KEY AUTO_INCREMENT,
    date VARCHAR(10) NOT NULL,
    name VARCHAR(100),
    total_count INT NOT NULL,
    remaining_count INT NOT NULL,
    sold_count INT NOT NULL,
    version INT NOT NULL DEFAULT 1,
    status INT NOT NULL DEFAULT 1,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_date (date)
);
```

### 乐观锁更新SQL
```sql
UPDATE ticket 
SET remaining_count = #{remainingCount},
    sold_count = #{soldCount},
    version = version + 1
WHERE id = #{id} AND version = #{currentVersion}
```

## 配置说明

### Redis配置
```properties
# application.properties
spring.redis.host=localhost
spring.redis.port=6379
spring.redis.database=0
```

### 乐观锁重试配置
```java
// 最大重试次数
int maxRetries = 5;

// 重试间隔（毫秒）
Thread.sleep(10);
```

## 监控和统计

### 1. 重试统计
- 总重试次数
- 平均重试次数
- 各票券重试次数分布

### 2. 操作日志
- 秒杀活动状态变更记录
- 库存修改操作记录
- 错误和异常记录

### 3. 性能指标
- 乐观锁冲突频率
- 重试成功率
- 库存修改响应时间

## 最佳实践

### 1. 选择合适方案
- **高并发、高一致性要求**：选择方案一（悲观锁）
- **用户体验优先**：选择方案二（乐观锁）

### 2. 乐观锁优化
- 合理设置最大重试次数
- 监控重试频率，及时调整策略
- 考虑使用指数退避算法

### 3. 监控告警
- 设置重试次数阈值告警
- 监控秒杀活动状态异常
- 跟踪库存数据一致性

### 4. 容错处理
- 乐观锁失败后的降级策略
- 网络异常的重试机制
- 数据不一致的补偿机制

## 故障排除

### 常见问题

#### 1. 乐观锁频繁重试
**原因**：并发冲突严重
**解决**：
- 减少并发修改频率
- 增加重试间隔
- 考虑使用悲观锁方案

#### 2. 秒杀活动无法暂停
**原因**：Redis连接异常
**解决**：
- 检查Redis服务状态
- 验证Redis连接配置
- 查看服务日志

#### 3. 库存数据不一致
**原因**：乐观锁更新失败
**解决**：
- 检查版本号字段
- 验证SQL语句
- 查看重试统计

### 日志分析
```bash
# 查看服务日志
tail -f logs/miaosha-service.log

# 查看错误日志
grep "ERROR" logs/miaosha-service.log

# 查看乐观锁重试日志
grep "乐观锁更新失败" logs/miaosha-service.log
```

## 扩展功能

### 1. 批量操作优化
- 支持批量暂停/恢复
- 批量库存修改
- 操作队列管理

### 2. 高级监控
- 实时性能监控
- 告警通知机制
- 数据一致性检查

### 3. 自动化运维
- 自动库存调整
- 智能重试策略
- 故障自动恢复

## 总结

本解决方案提供了两种完整的秒杀过程中修改余票数量的方案，满足不同业务场景的需求：

1. **悲观锁方案**：适合对数据一致性要求极高的场景
2. **乐观锁方案**：适合对用户体验要求高的场景

通过合理的架构设计和实现，系统能够：
- 灵活控制秒杀活动状态
- 安全地修改库存数据
- 提供完整的监控和统计
- 支持故障排除和优化

建议根据实际业务需求选择合适的方案，并持续监控和优化系统性能。
