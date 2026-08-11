# TicketCacheService 票券缓存服务使用说明

## 概述

本项目创建了两个票券缓存相关的服务：

1. **TicketCacheService** - 提供从缓存和数据库分别获取票券数据的方法
2. **TicketCacheManager** - 提供票券缓存的基本操作方法

## 服务架构

```
TicketCacheService (接口)
    ↓
TicketCacheServiceImpl (实现类)
    ↓
Redis + 数据库

TicketCacheManager (接口)
    ↓
TicketCacheManagerImpl (实现类)
    ↓
Redis
```

## 主要功能

### TicketCacheService

#### 1. 仅从缓存获取数据
- `getTicketFromCache(String date)` - 从缓存获取指定日期的票券
- `getAllTicketsFromCache()` - 从缓存获取所有票券列表

#### 2. 仅从数据库获取数据
- `getTicketFromDatabase(String date)` - 从数据库获取指定日期的票券
- `getAllTicketsFromDatabase()` - 从数据库获取所有票券列表

#### 3. 智能缓存回退
- `getTicketWithCacheFallback(String date)` - 先从缓存获取，缓存未命中则从数据库获取并更新缓存
- `getAllTicketsWithCacheFallback()` - 先从缓存获取，缓存未命中则从数据库获取并更新缓存

#### 4. 缓存管理
- `saveTicketToCache(String date, Ticket ticket)` - 保存票券到缓存
- `saveTicketListToCache(List<Ticket> tickets)` - 保存票券列表到缓存
- `deleteTicketFromCache(String date)` - 删除指定日期的票券缓存
- `clearAllTicketCache()` - 清空所有票券缓存

#### 5. 连接状态检查
- `isRedisConnected()` - 检查Redis连接状态

### TicketCacheManager

#### 1. 票券缓存操作
- `getTicket(String date)` - 获取指定日期的票券
- `saveTicket(String date, Ticket ticket)` - 保存票券到缓存
- `deleteTicket(String date)` - 删除指定日期的票券缓存
- `getTicketList()` - 获取票券列表
- `saveTicketList(List<Ticket> tickets)` - 保存票券列表到缓存

#### 2. 购买记录缓存
- `addPurchaseRecord(Long userId, PurchaseRecord record)` - 添加购买记录到缓存
- `getPurchaseRecords(Long userId)` - 获取用户的购买记录

#### 3. 缓存管理
- `clearAllTicketCache()` - 清空所有票券缓存
- `isRedisConnected()` - 检查Redis连接状态

## 使用示例

### 1. 注入服务

```java
@Service
public class YourService {
    
    @Resource
    private TicketCacheService ticketCacheService;
    
    @Resource
    private TicketCacheManager ticketCacheManager;
}
```

### 2. 从缓存获取票券

```java
// 仅从缓存获取
Ticket cachedTicket = ticketCacheService.getTicketFromCache("2024-01-15");
if (cachedTicket != null) {
    // 使用缓存数据
    System.out.println("缓存命中: " + cachedTicket);
} else {
    // 缓存未命中
    System.out.println("缓存未命中");
}
```

### 3. 从数据库获取票券

```java
// 仅从数据库获取
Ticket dbTicket = ticketCacheService.getTicketFromDatabase("2024-01-15");
if (dbTicket != null) {
    // 使用数据库数据
    System.out.println("数据库数据: " + dbTicket);
} else {
    // 数据库中不存在
    System.out.println("数据库中不存在该票券");
}
```

### 4. 智能缓存回退

```java
// 推荐使用：智能缓存回退
Ticket ticket = ticketCacheService.getTicketWithCacheFallback("2024-01-15");
// 这个方法会：
// 1. 先从缓存获取
// 2. 如果缓存未命中，从数据库获取
// 3. 将数据库数据更新到缓存
// 4. 返回票券数据
```

### 5. 保存票券到缓存

```java
// 创建票券
Ticket ticket = new Ticket("2024-01-15", 100);
ticket.setRemaining(80);

// 保存到缓存
ticketCacheService.saveTicketToCache("2024-01-15", ticket);
```

### 6. 使用TicketCacheManager

```java
// 保存票券
ticketCacheManager.saveTicket("2024-01-15", ticket);

// 获取票券
Ticket retrievedTicket = ticketCacheManager.getTicket("2024-01-15");

// 获取票券列表
List<Ticket> ticketList = ticketCacheManager.getTicketList();

// 保存票券列表
ticketCacheManager.saveTicketList(ticketList);
```

## 缓存策略

### 1. 缓存过期时间
- 票券缓存：1小时（3600秒）
- 票券列表缓存：1小时（3600秒）
- 购买记录缓存：1小时（3600秒）

### 2. 缓存键命名规则
- 单个票券：`ticket:2024-01-15`
- 票券列表：`ticket:list`
- 购买记录：`purchase:12345`（用户ID）

### 3. 缓存更新策略
- **Cache-Aside Pattern**: 先更新数据库，再删除缓存
- **Write-Through Pattern**: 同时更新数据库和缓存
- **Read-Through Pattern**: 缓存未命中时自动从数据库加载并更新缓存

## 最佳实践

### 1. 选择合适的获取方法

```java
// 场景1：需要最新数据，不关心性能
Ticket ticket = ticketCacheService.getTicketFromDatabase(date);

// 场景2：优先使用缓存，可以接受稍微过期的数据
Ticket ticket = ticketCacheService.getTicketFromCache(date);

// 场景3：推荐使用，平衡性能和一致性
Ticket ticket = ticketCacheService.getTicketWithCacheFallback(date);
```

### 2. 缓存预热

```java
// 系统启动时预热缓存
@PostConstruct
public void warmUpCache() {
    List<Ticket> tickets = ticketCacheService.getAllTicketsFromDatabase();
    ticketCacheService.saveTicketListToCache(tickets);
}
```

### 3. 缓存失效处理

```java
// 数据更新后，及时删除相关缓存
public void updateTicket(String date, Ticket ticket) {
    // 1. 更新数据库
    ticketEntityMapper.update(ticket);
    
    // 2. 删除缓存
    ticketCacheService.deleteTicketFromCache(date);
    
    // 3. 可选：重新加载到缓存
    ticketCacheService.saveTicketToCache(date, ticket);
}
```

### 4. 异常处理

```java
try {
    Ticket ticket = ticketCacheService.getTicketWithCacheFallback(date);
    // 处理票券数据
} catch (Exception e) {
    LOGGER.error("获取票券失败", e);
    // 降级处理：直接查询数据库
    Ticket fallbackTicket = ticketCacheService.getTicketFromDatabase(date);
}
```

## 性能优化建议

### 1. 批量操作
```java
// 批量获取票券
List<Ticket> tickets = ticketCacheService.getAllTicketsWithCacheFallback();

// 批量更新缓存
ticketCacheService.saveTicketListToCache(tickets);
```

### 2. 异步更新缓存
```java
@Async
public void updateCacheAsync(String date, Ticket ticket) {
    ticketCacheService.saveTicketToCache(date, ticket);
}
```

### 3. 缓存穿透防护
```java
// 对于不存在的票券，也缓存一个空值，避免缓存穿透
if (ticket == null) {
    // 缓存空值，设置较短的过期时间
    stringRedisTemplate.opsForValue().set(key, "NULL", 300, TimeUnit.SECONDS);
}
```

## 监控和调试

### 1. 日志级别
- DEBUG：详细的缓存操作日志
- INFO：重要的缓存操作结果
- ERROR：缓存操作异常

### 2. 连接状态监控
```java
// 定期检查Redis连接状态
@Scheduled(fixedRate = 60000) // 每分钟检查一次
public void checkRedisConnection() {
    boolean isConnected = ticketCacheService.isRedisConnected();
    if (!isConnected) {
        LOGGER.error("Redis连接异常，需要检查");
        // 发送告警通知
    }
}
```

### 3. 缓存命中率统计
```java
// 可以添加缓存命中率统计
private AtomicLong cacheHits = new AtomicLong(0);
private AtomicLong cacheMisses = new AtomicLong(0);

public double getCacheHitRate() {
    long hits = cacheHits.get();
    long misses = cacheMisses.get();
    long total = hits + misses;
    return total > 0 ? (double) hits / total : 0.0;
}
```

## 总结

TicketCacheService 和 TicketCacheManager 提供了完整的票券缓存解决方案：

1. **灵活性**：可以选择从缓存或数据库获取数据
2. **智能性**：提供缓存回退机制，自动处理缓存未命中的情况
3. **可靠性**：包含异常处理和连接状态检查
4. **性能**：通过缓存减少数据库访问，提高系统响应速度
5. **一致性**：支持缓存更新和失效，保证数据一致性

建议在实际使用中，优先使用 `getTicketWithCacheFallback()` 方法，它提供了最佳的缓存策略平衡。
