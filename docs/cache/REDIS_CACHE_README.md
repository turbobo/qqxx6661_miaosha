# Redis 缓存系统说明

## 概述

本系统已经将票券数据从内存缓存迁移到 Redis 缓存，提供了更好的性能和可扩展性。

## 主要特性

### 1. 自动初始化
- 系统启动时自动从数据库查询票券数据
- 如果数据库中没有数据，自动创建默认票券
- 所有数据自动同步到 Redis 缓存

### 2. 智能缓存策略
- **先查缓存，后查数据库**：优先从 Redis 获取数据，提高响应速度
- **自动更新缓存**：数据库数据变更时自动同步到 Redis
- **缓存过期管理**：设置 24 小时过期时间，避免数据过期

### 3. 缓存一致性
- 创建、更新、删除票券时自动更新 Redis 缓存
- 购买票券时实时更新库存缓存
- 支持手动清空缓存功能

## 配置说明

### Redis 连接配置

在 `miaosha-service/src/main/resources/application.properties` 中配置：

```properties
spring.redis.host=localhost
spring.redis.port=6379
spring.redis.database=0
spring.redis.timeout=5000ms
spring.redis.lettuce.pool.max-active=8
spring.redis.lettuce.pool.max-wait=-1ms
spring.redis.lettuce.pool.max-idle=8
spring.redis.lettuce.pool.min-idle=0
```

### 依赖配置

确保 `miaosha-service/pom.xml` 包含 Redis 依赖：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

## 核心组件

### 1. RedisConfig
- 配置 RedisTemplate
- 设置序列化方式（Jackson2JsonRedisSerializer）
- 配置连接池参数

### 2. RedisUtil
- 提供基础的 Redis 操作方法
- 支持字符串、对象、列表等数据类型
- 提供过期时间管理

### 3. TicketCacheManager
- 专门管理票券相关的缓存操作
- 提供票券、购买记录的缓存方法
- 支持缓存统计和健康检查

### 4. TicketServiceImpl
- 业务逻辑层，集成 Redis 缓存
- 实现缓存优先的数据访问策略
- 自动维护缓存一致性

## API 接口

### Redis 健康检查

#### 检查 Redis 连接状态
```
GET /api/redis/health
```

响应示例：
```json
{
    "status": "UP",
    "message": "Redis连接正常",
    "timestamp": 1640995200000
}
```

#### 清空票券缓存
```
GET /api/redis/cache/clear
```

响应示例：
```json
{
    "status": "SUCCESS",
    "message": "票券缓存清空成功",
    "timestamp": 1640995200000
}
```

#### 获取缓存统计信息
```
GET /api/redis/cache/stats
```

响应示例：
```json
{
    "status": "SUCCESS",
    "message": "缓存统计信息获取成功",
    "redisConnected": true,
    "ticketListCached": true,
    "ticketListSize": 3,
    "todayTicketCached": true,
    "timestamp": 1640995200000
}
```

## 缓存键结构

### 票券缓存
- 单个票券：`ticket:{日期}` (例如：`ticket:2025-08-11`)
- 票券列表：`ticket:recent_list`

### 购买记录缓存
- 用户购买记录：`purchase_record:user:{用户ID}` (例如：`purchase_record:user:user123`)

## 使用示例

### 1. 启动系统
```bash
# 确保 Redis 服务已启动
redis-server

# 启动 Spring Boot 应用
mvn spring-boot:run
```

### 2. 检查 Redis 连接
```bash
curl http://localhost:8081/api/redis/health
```

### 3. 查看缓存状态
```bash
curl http://localhost:8081/api/redis/cache/stats
```

### 4. 清空缓存
```bash
curl http://localhost:8080/api/redis/cache/clear
```

## 性能优化建议

### 1. Redis 配置优化
- 启用 Redis 持久化（RDB + AOF）
- 配置合适的内存策略
- 启用 Redis 集群（生产环境）

### 2. 缓存策略优化
- 根据业务需求调整缓存过期时间
- 实现缓存预热机制
- 添加缓存穿透保护

### 3. 监控和告警
- 监控 Redis 内存使用率
- 监控缓存命中率
- 设置缓存异常告警

## 故障排除

### 1. Redis 连接失败
- 检查 Redis 服务是否启动
- 验证连接配置是否正确
- 检查防火墙设置

### 2. 缓存数据不一致
- 使用 `/api/redis/cache/clear` 清空缓存
- 检查数据库数据是否正确
- 查看应用日志排查问题

### 3. 性能问题
- 检查 Redis 内存使用情况
- 分析缓存命中率
- 优化缓存键设计

## 注意事项

1. **数据一致性**：Redis 缓存作为数据库的补充，最终一致性由数据库保证
2. **缓存过期**：票券数据设置 24 小时过期，确保数据不会无限期占用内存
3. **异常处理**：Redis 异常时系统会自动降级到数据库查询
4. **内存管理**：定期监控 Redis 内存使用，避免内存溢出

## 扩展功能

### 1. 分布式锁
可以基于 Redis 实现分布式锁，用于秒杀场景的并发控制。

### 2. 消息队列
利用 Redis 的发布订阅功能，实现票券状态变更通知。

### 3. 限流器
基于 Redis 实现 API 限流，保护系统免受恶意请求攻击。

## 总结

Redis 缓存系统的引入显著提升了系统的性能和可扩展性：
- **性能提升**：缓存命中时响应时间从毫秒级降低到微秒级
- **可扩展性**：支持多实例部署，缓存数据共享
- **可靠性**：Redis 异常时自动降级到数据库查询
- **易维护**：提供完整的监控和管理接口

通过合理使用 Redis 缓存，系统能够更好地应对高并发场景，为用户提供更流畅的体验。
