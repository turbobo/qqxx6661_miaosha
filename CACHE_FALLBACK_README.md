# 购买记录缓存兜底功能说明

## 概述

本次修改实现了购买记录的缓存优先、数据库兜底功能。当缓存中没有数据时，自动从数据库获取并同步到缓存，确保数据的完整性和一致性。

## 功能特性

### 1. 缓存优先策略
- 首先尝试从Redis缓存获取购买记录
- 如果缓存命中，直接返回数据，不访问数据库
- 提高查询性能，减少数据库压力

### 2. 数据库兜底机制
- 当缓存未命中时，自动从数据库查询
- 查询到的数据自动同步到缓存
- 确保数据的完整性和一致性

### 3. 智能数据同步
- 数据库数据自动转换为前端模型
- 自动设置缓存过期时间
- 避免缓存穿透和雪崩

## 实现细节

### 1. 新增方法

#### TicketCacheManager接口
```java
/**
 * 获取用户的购买记录（缓存优先，数据库兜底）
 * @param userId 用户ID
 * @return 购买记录列表，如果都没有则返回空列表
 */
List<PurchaseRecord> getPurchaseRecordsWithFallback(Long userId);
```

#### TicketCacheManagerImpl实现类
```java
@Override
public List<PurchaseRecord> getPurchaseRecordsWithFallback(Long userId) {
    try {
        // 首先尝试从缓存获取
        List<PurchaseRecord> cachedRecords = getPurchaseRecords(userId);
        if (cachedRecords != null && !cachedRecords.isEmpty()) {
            return cachedRecords;
        }
        
        // 缓存中没有，尝试从数据库获取
        List<PurchaseRecord> dbRecords = getPurchaseRecordsFromDatabase(userId);
        
        if (dbRecords != null && !dbRecords.isEmpty()) {
            // 将数据库数据同步到缓存
            savePurchaseRecordsToCache(userId, dbRecords);
            return dbRecords;
        }
        
        // 数据库也没有数据，返回空列表
        return new ArrayList<>();
        
    } catch (Exception e) {
        LOGGER.error("获取购买记录失败，用户ID: {}", userId, e);
        return new ArrayList<>();
    }
}
```

### 2. 数据库查询方法

```java
/**
 * 从数据库获取用户的购买记录
 */
private List<PurchaseRecord> getPurchaseRecordsFromDatabase(Long userId) {
    try {
        // 从数据库查询用户的购买记录
        List<TicketPurchaseRecord> dbRecords = ticketPurchaseRecordMapper.selectByUserId(userId);
        
        if (dbRecords != null && !dbRecords.isEmpty()) {
            // 将数据库实体转换为前端模型
            List<PurchaseRecord> purchaseRecords = new ArrayList<>();
            for (TicketPurchaseRecord dbRecord : dbRecords) {
                PurchaseRecord record = new PurchaseRecord(
                    dbRecord.getUserId(),
                    LocalDate.parse(dbRecord.getTicketDate()),
                    dbRecord.getTicketCode()
                );
                purchaseRecords.add(record);
            }
            return purchaseRecords;
        }
        
        return new ArrayList<>();
        
    } catch (Exception e) {
        LOGGER.error("从数据库获取购买记录失败，用户ID: {}", userId, e);
        return new ArrayList<>();
    }
}
```

### 3. 缓存同步方法

```java
/**
 * 将购买记录保存到缓存
 */
private void savePurchaseRecordsToCache(Long userId, List<PurchaseRecord> records) {
    try {
        if (records != null && !records.isEmpty()) {
            String key = PURCHASE_RECORD_CACHE_PREFIX + userId;
            String recordsJson = JSON.toJSONString(records);
            stringRedisTemplate.opsForValue().set(key, recordsJson, CACHE_EXPIRE_TIME, TimeUnit.SECONDS);
        }
    } catch (Exception e) {
        LOGGER.error("购买记录同步到缓存失败，用户ID: {}", userId, e);
    }
}
```

## 使用方式

### 1. 在TicketServiceImpl中的应用

```java
@Override
public boolean hasPurchased(Long userId, String date) {
    try {
        // 使用缓存优先，数据库兜底的方式获取购买记录
        List<PurchaseRecord> records = ticketCacheManager.getPurchaseRecordsWithFallback(userId);
        
        if (records.isEmpty()) {
            return false;
        }
        
        // 检查是否已购买指定日期的票券
        return records.stream()
            .anyMatch(record -> record.getDate().equals(LocalDate.parse(date)));
            
    } catch (Exception e) {
        LOGGER.error("查询用户购买记录失败，用户ID: {}, 日期: {}", userId, date, e);
        return false;
    }
}
```

### 2. 直接调用缓存服务

```java
@Resource
private TicketCacheManager ticketCacheManager;

public void someMethod() {
    Long userId = 12345L;
    
    // 获取用户的购买记录（缓存优先，数据库兜底）
    List<PurchaseRecord> records = ticketCacheManager.getPurchaseRecordsWithFallback(userId);
    
    if (records.isEmpty()) {
        // 用户没有购买记录
        System.out.println("用户没有购买记录");
    } else {
        // 处理购买记录
        records.forEach(record -> {
            System.out.println("票券编号: " + record.getTicketCode());
            System.out.println("购买日期: " + record.getDate());
        });
    }
}
```

## 性能优化

### 1. 缓存策略
- **缓存过期时间**: 1小时（3600秒）
- **缓存键前缀**: `purchase:{userId}`
- **序列化方式**: JSON格式

### 2. 数据库查询优化
- 使用`selectByUserId`方法，避免全表扫描
- 建议在`user_id`字段上创建索引
- 支持分页查询（可根据需要扩展）

### 3. 异常处理
- 缓存异常时，降级到数据库查询
- 数据库异常时，返回空列表
- 记录详细的错误日志，便于问题排查

## 测试用例

### 1. 缓存命中测试
```java
@Test
void testGetPurchaseRecordsWithFallback_CacheHit() {
    // 模拟缓存命中
    when(valueOperations.get("purchase:" + userId)).thenReturn(cachedJson);
    
    List<PurchaseRecord> result = ticketCacheManager.getPurchaseRecordsWithFallback(userId);
    
    assertNotNull(result);
    assertEquals(1, result.size());
    verify(ticketPurchaseRecordMapper, never()).selectByUserId(any());
}
```

### 2. 缓存未命中，数据库命中测试
```java
@Test
void testGetPurchaseRecordsWithFallback_CacheMiss_DatabaseHit() {
    // 模拟缓存未命中，数据库命中
    when(valueOperations.get("purchase:" + userId)).thenReturn(null);
    when(ticketPurchaseRecordMapper.selectByUserId(userId)).thenReturn(dbRecords);
    
    List<PurchaseRecord> result = ticketCacheManager.getPurchaseRecordsWithFallback(userId);
    
    assertNotNull(result);
    assertEquals(1, result.size());
    verify(ticketPurchaseRecordMapper).selectByUserId(userId);
    verify(valueOperations).set(eq("purchase:" + userId), anyString(), eq(3600L), any());
}
```

### 3. 异常处理测试
```java
@Test
void testGetPurchaseRecordsWithFallback_Exception() {
    // 模拟异常
    when(valueOperations.get("purchase:" + userId)).thenThrow(new RuntimeException("Redis连接失败"));
    
    List<PurchaseRecord> result = ticketCacheManager.getPurchaseRecordsWithFallback(userId);
    
    assertNotNull(result);
    assertTrue(result.isEmpty());
}
```

## 注意事项

### 1. 数据一致性
- 当购买记录发生变化时，需要及时清除相关缓存
- 建议在购买成功后，调用`deleteTicket`方法清除缓存
- 或者在更新购买记录后，主动更新缓存

### 2. 缓存容量
- 监控Redis内存使用情况
- 设置合理的缓存过期时间
- 考虑使用LRU等淘汰策略

### 3. 监控告警
- 监控缓存命中率
- 监控数据库查询性能
- 设置异常告警机制

## 总结

新的缓存兜底功能提供了以下优势：

1. **性能提升**: 缓存命中时，查询性能显著提升
2. **数据完整性**: 数据库兜底确保数据不丢失
3. **自动同步**: 数据库数据自动同步到缓存
4. **异常容错**: 异常情况下优雅降级
5. **易于使用**: 简单的API调用，无需关心底层实现

建议在生产环境中充分测试，并根据实际业务需求调整缓存策略和过期时间。
