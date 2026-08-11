# 购买记录缓存Key结构修改说明

## 概述

本次修改将购买记录的缓存key从`purchase:{userId}`改为`purchase:{userId}:{date}`，实现了按用户ID和购买日期分别缓存购买记录的功能。

## 缓存Key结构变化

### 修改前
```
purchase:12345 -> [购买记录列表的JSON]
```

### 修改后
```
purchase:12345:2024-01-15 -> 单个购买记录的JSON
purchase:12345:2024-01-16 -> 单个购买记录的JSON
purchase:12345:2024-01-17 -> 单个购买记录的JSON
```

## 优势

### 1. 精确缓存
- 每个购买记录独立缓存，避免数据冗余
- 支持按日期精确查询，提高缓存命中率
- 减少不必要的数据传输

### 2. 灵活管理
- 可以单独删除某个日期的购买记录缓存
- 支持按日期范围批量操作
- 便于缓存预热和清理

### 3. 性能提升
- 减少序列化/反序列化的数据量
- 提高Redis内存使用效率
- 支持更细粒度的缓存策略

## 接口变化

### 1. 新增方法

#### 按日期获取购买记录
```java
/**
 * 获取用户的指定日期购买记录（仅从缓存）
 */
PurchaseRecord getPurchaseRecord(Long userId, String date);

/**
 * 获取用户的指定日期购买记录（缓存优先，数据库兜底）
 */
PurchaseRecord getPurchaseRecordWithFallback(Long userId, String date);
```

#### 修改的方法签名
```java
/**
 * 添加购买记录到缓存
 * 修改前: void addPurchaseRecord(Long userId, PurchaseRecord record)
 * 修改后: void addPurchaseRecord(Long userId, String date, PurchaseRecord record)
 */
void addPurchaseRecord(Long userId, String date, PurchaseRecord record);
```

### 2. 保持兼容的方法

```java
/**
 * 获取用户的所有购买记录（仅从缓存）
 * 内部实现改为pattern匹配获取所有相关key
 */
List<PurchaseRecord> getPurchaseRecords(Long userId);

/**
 * 获取用户的所有购买记录（缓存优先，数据库兜底）
 */
List<PurchaseRecord> getPurchaseRecordsWithFallback(Long userId);
```

## 实现细节

### 1. 缓存Key生成

```java
// 单个购买记录缓存key
String key = PURCHASE_RECORD_CACHE_PREFIX + userId + ":" + date;
// 例如: purchase:12345:2024-01-15

// 用户所有购买记录pattern
String pattern = PURCHASE_RECORD_CACHE_PREFIX + userId + ":*";
// 例如: purchase:12345:*
```

### 2. 数据存储结构

```java
// 修改前：存储购买记录列表
String recordsJson = JSON.toJSONString(records);
stringRedisTemplate.opsForValue().set(key, recordsJson, CACHE_EXPIRE_TIME, TimeUnit.SECONDS);

// 修改后：存储单个购买记录
String recordJson = JSON.toJSONString(record);
stringRedisTemplate.opsForValue().set(key, recordJson, CACHE_EXPIRE_TIME, TimeUnit.SECONDS);
```

### 3. 获取用户所有购买记录

```java
@Override
public List<PurchaseRecord> getPurchaseRecords(Long userId) {
    try {
        // 使用pattern匹配获取所有相关的key
        String pattern = PURCHASE_RECORD_CACHE_PREFIX + userId + ":*";
        Set<String> keys = stringRedisTemplate.keys(pattern);
        
        if (keys != null && !keys.isEmpty()) {
            List<PurchaseRecord> records = new ArrayList<>();
            for (String key : keys) {
                String recordJson = stringRedisTemplate.opsForValue().get(key);
                if (recordJson != null) {
                    PurchaseRecord record = JSON.parseObject(recordJson, PurchaseRecord.class);
                    records.add(record);
                }
            }
            return records;
        }
        
        return new ArrayList<>();
    } catch (Exception e) {
        LOGGER.error("从缓存获取用户购买记录失败，用户ID: {}", userId, e);
        return new ArrayList<>();
    }
}
```

## 使用示例

### 1. 添加购买记录

```java
@Resource
private TicketCacheManager ticketCacheManager;

public void addPurchaseRecord(Long userId, String date, PurchaseRecord record) {
    // 新的方法签名，需要传入日期
    ticketCacheManager.addPurchaseRecord(userId, date, record);
}
```

### 2. 查询指定日期的购买记录

```java
public boolean hasPurchased(Long userId, String date) {
    // 使用新的方法，直接查询指定日期的购买记录
    PurchaseRecord record = ticketCacheManager.getPurchaseRecordWithFallback(userId, date);
    return record != null;
}
```

### 3. 查询用户所有购买记录

```java
public List<PurchaseRecord> getUserPurchaseRecords(Long userId) {
    // 获取用户的所有购买记录
    return ticketCacheManager.getPurchaseRecordsWithFallback(userId);
}
```

## 缓存管理

### 1. 删除指定日期的购买记录缓存

```java
public void deletePurchaseRecordCache(Long userId, String date) {
    String key = "purchase:" + userId + ":" + date;
    stringRedisTemplate.delete(key);
}
```

### 2. 删除用户所有购买记录缓存

```java
public void deleteUserPurchaseRecordCache(Long userId) {
    String pattern = "purchase:" + userId + ":*";
    Set<String> keys = stringRedisTemplate.keys(pattern);
    if (keys != null && !keys.isEmpty()) {
        stringRedisTemplate.delete(keys);
    }
}
```

### 3. 批量预热缓存

```java
public void warmUpPurchaseRecordCache(Long userId, List<PurchaseRecord> records) {
    for (PurchaseRecord record : records) {
        String date = record.getDate().toString();
        ticketCacheManager.addPurchaseRecord(userId, date, record);
    }
}
```

## 注意事项

### 1. 性能考虑
- `keys`命令在生产环境中要谨慎使用，特别是在数据量大的情况下
- 建议使用`scan`命令替代`keys`命令进行pattern匹配
- 考虑使用Redis的Sorted Set来管理用户购买记录的时间序列

### 2. 数据一致性
- 当购买记录发生变化时，需要及时清除相关缓存
- 建议在购买成功后，调用`deletePurchaseRecordCache`方法清除缓存
- 或者在更新购买记录后，主动更新缓存

### 3. 缓存策略
- 根据业务需求调整缓存过期时间
- 考虑使用LRU等淘汰策略
- 监控Redis内存使用情况

## 迁移建议

### 1. 渐进式迁移
- 新功能使用新的缓存key结构
- 旧功能可以继续使用原有的缓存结构
- 通过定时任务逐步迁移现有缓存数据

### 2. 数据清理
- 在迁移完成后，清理旧的缓存数据
- 使用`scan`命令安全地删除旧的缓存key
- 监控缓存命中率的变化

### 3. 回滚方案
- 保留原有的缓存逻辑作为备选方案
- 如果新结构出现问题，可以快速回滚到旧结构
- 记录详细的迁移日志，便于问题排查

## 总结

新的缓存key结构提供了以下优势：

1. **精确缓存**: 按日期分别缓存，提高缓存命中率
2. **灵活管理**: 支持细粒度的缓存操作
3. **性能提升**: 减少数据传输，提高Redis效率
4. **易于扩展**: 支持更复杂的缓存策略

建议在生产环境中充分测试，并根据实际业务需求调整缓存策略。
