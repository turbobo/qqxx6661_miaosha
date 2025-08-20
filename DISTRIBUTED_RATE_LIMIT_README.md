# 分布式限流服务使用说明

## 概述

本项目实现了基于Redis+Lua脚本的分布式限流服务，支持多种限流算法和灵活的配置选项。相比单机的Guava RateLimiter，分布式限流可以在多实例环境下保持一致的限流效果。

## 架构设计

```
用户请求 → Controller → @DistributedRateLimit注解 → AOP切面 → Redis+Lua脚本 → 限流决策
```

## 核心组件

### 1. 分布式限流服务接口
- **`DistributedRateLimitService`** - 定义限流服务的基本方法
- **`DistributedRateLimitServiceImpl`** - 实现基于Redis+Lua脚本的限流逻辑

### 2. 限流注解
- **`@DistributedRateLimit`** - 用于标记需要限流的方法，支持多种配置选项

### 3. Lua脚本
- **`rate_limit.lua`** - 实现滑动时间窗口限流算法
- **`token_bucket.lua`** - 实现令牌桶限流算法

### 4. 配置类
- **`RateLimitConfig`** - 配置Redis脚本和限流服务Bean

## 限流算法

### 1. 滑动时间窗口算法
- **原理**：使用Redis的ZSet数据结构，记录每个请求的时间戳
- **特点**：精确控制时间窗口，自动清理过期记录
- **适用场景**：需要精确控制请求频率的场景

### 2. 令牌桶算法
- **原理**：维护一个令牌桶，按照固定速率填充令牌，请求消耗令牌
- **特点**：支持突发流量，平滑限流
- **适用场景**：需要处理突发流量的场景

## 使用方法

### 1. 使用注解进行限流

#### 基本用法
```java
@DistributedRateLimit(
    key = "api:purchase_ticket", 
    limit = 10, 
    window = 60
)
@PostMapping("/purchase")
public ApiResponse<PurchaseRecord> purchaseTicket(@RequestBody PurchaseRequest request) {
    // 业务逻辑
}
```

#### 高级配置
```java
@DistributedRateLimit(
    key = "#userId",                    // 支持SpEL表达式
    limit = 5,                          // 限流次数
    window = 60,                        // 时间窗口（秒）
    type = RateLimitType.TOKEN_BUCKET,  // 限流算法类型
    message = "请求过于频繁，请稍后再试",    // 自定义错误消息
    block = true,                       // 是否阻塞等待
    timeout = 2000                      // 阻塞超时时间（毫秒）
)
@GetMapping("/user-specific")
public Map<String, Object> userSpecificApi(@RequestParam String userId) {
    // 业务逻辑
}
```

### 2. 手动调用限流服务

#### 滑动时间窗口限流
```java
@Resource
private DistributedRateLimitService distributedRateLimitService;

public void someMethod() {
    // 尝试获取令牌：每分钟最多10次
    boolean allowed = distributedRateLimitService.tryAcquire("key", 10, 60);
    
    if (allowed) {
        // 限流通过，执行业务逻辑
        doBusinessLogic();
    } else {
        // 限流拒绝，返回错误
        throw new RuntimeException("请求过于频繁");
    }
}
```

#### 令牌桶限流
```java
public void someMethod() {
    // 尝试获取令牌：桶容量10，每秒填充2个令牌
    boolean allowed = ((DistributedRateLimitServiceImpl) distributedRateLimitService)
        .tryAcquireWithTokenBucket("key", 10, 2.0, 1);
    
    if (allowed) {
        // 限流通过，执行业务逻辑
        doBusinessLogic();
    } else {
        // 限流拒绝，返回错误
        throw new RuntimeException("请求过于频繁");
    }
}
```

### 3. 在TicketServiceImpl中的应用

```java
/**
 * 限流检查
 */
private void validRateLimit(PurchaseRequest request) {
    // 用户限流：每个用户，1分钟10次抢购
    boolean allowed = userService.isAllowed(request.getUserId(), 5, 60);
    if (!allowed) {
        throw new BusinessException("购买失败，用户访问超过频率限制");
    }

    // 分布式接口限流：使用Redis+Lua脚本
    // 全局接口限流：每秒放行10个请求
    String globalRateLimitKey = "global:purchase_ticket";
    if (!distributedRateLimitService.tryAcquire(globalRateLimitKey, 10, 1)) {
        LOGGER.warn("全局接口限流：请求过于频繁");
        throw new BusinessException("系统繁忙，请稍后再试");
    }
    
    // 用户接口限流：每个用户每分钟最多10次请求
    String userRateLimitKey = "user:" + request.getUserId() + ":purchase_ticket";
    if (!distributedRateLimitService.tryAcquire(userRateLimitKey, 10, 60)) {
        LOGGER.warn("用户接口限流：用户{}请求过于频繁", request.getUserId());
        throw new BusinessException("您的请求过于频繁，请稍后再试");
    }
}
```

## 配置选项

### 1. 注解配置参数

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `key` | String | "" | 限流键，支持SpEL表达式 |
| `limit` | int | 10 | 限流次数 |
| `window` | int | 60 | 时间窗口（秒） |
| `type` | RateLimitType | SLIDING_WINDOW | 限流算法类型 |
| `message` | String | "请求过于频繁，请稍后再试" | 限流失败时的错误消息 |
| `block` | boolean | false | 是否阻塞等待 |
| `timeout` | long | 1000 | 阻塞等待超时时间（毫秒） |

### 2. 限流算法类型

```java
public enum RateLimitType {
    /**
     * 滑动时间窗口
     */
    SLIDING_WINDOW,
    
    /**
     * 令牌桶
     */
    TOKEN_BUCKET
}
```

## 测试接口

### 1. 滑动时间窗口限流测试
```bash
# 测试滑动时间窗口限流（每分钟最多10次）
GET /api/rate-limit-demo/sliding-window
```

### 2. 令牌桶限流测试
```bash
# 测试令牌桶限流
GET /api/rate-limit-demo/token-bucket
```

### 3. 用户个性化限流测试
```bash
# 测试基于用户ID的个性化限流
GET /api/rate-limit-demo/user-specific?userId=12345
```

### 4. 阻塞等待限流测试
```bash
# 测试阻塞等待模式
GET /api/rate-limit-demo/blocking
```

### 5. 手动限流测试
```bash
# 手动测试滑动时间窗口限流
GET /api/rate-limit-demo/manual/sliding-window?key=test_key

# 手动测试令牌桶限流
GET /api/rate-limit-demo/manual/token-bucket?key=test_key
```

### 6. 限流信息查询
```bash
# 查询限流信息
GET /api/rate-limit-demo/info/test_key

# 重置限流计数器
POST /api/rate-limit-demo/reset/test_key
```

## 性能特点

### 1. 优势
- **分布式一致性**：多实例环境下保持一致的限流效果
- **高性能**：使用Redis+Lua脚本，减少网络往返
- **精确控制**：支持毫秒级的时间窗口控制
- **灵活配置**：支持多种限流算法和个性化配置
- **自动清理**：自动清理过期数据，避免内存泄漏

### 2. 注意事项
- **Redis依赖**：需要Redis服务支持
- **网络延迟**：Redis网络延迟会影响限流响应时间
- **内存使用**：大量限流键可能占用较多Redis内存

## 最佳实践

### 1. 限流键设计
```java
// 全局接口限流
"global:purchase_ticket"

// 用户个性化限流
"user:{userId}:purchase_ticket"

// 接口+用户组合限流
"api:purchase_ticket:user:{userId}"

// 时间维度限流
"rate_limit:{date}:{hour}:{api}"
```

### 2. 限流参数设置
```java
// 高频接口：每秒限流
@DistributedRateLimit(limit = 100, window = 1)

// 中频接口：每分钟限流
@DistributedRateLimit(limit = 1000, window = 60)

// 低频接口：每小时限流
@DistributedRateLimit(limit = 10000, window = 3600)
```

### 3. 异常处理
```java
try {
    boolean allowed = distributedRateLimitService.tryAcquire(key, limit, window);
    if (allowed) {
        // 执行业务逻辑
    } else {
        // 处理限流拒绝
    }
} catch (Exception e) {
    // 限流服务异常时的降级处理
    LOGGER.warn("限流服务异常，降级处理", e);
    // 可以选择放行或拒绝
}
```

### 4. 监控和告警
```java
// 定期检查限流状态
@Scheduled(fixedRate = 60000)
public void checkRateLimitStatus() {
    // 检查Redis连接状态
    // 统计限流拒绝次数
    // 发送告警通知
}
```

## 总结

分布式限流服务提供了完整的限流解决方案：

1. **灵活性**：支持多种限流算法和配置选项
2. **高性能**：基于Redis+Lua脚本，减少网络开销
3. **分布式**：多实例环境下保持一致的限流效果
4. **易用性**：支持注解和手动调用两种方式
5. **可扩展**：支持SpEL表达式和个性化配置

建议在实际使用中，根据业务需求选择合适的限流算法和参数配置，并做好监控和告警。
