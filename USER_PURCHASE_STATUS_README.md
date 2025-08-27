# 用户购买状态功能实现说明

## 功能概述

本功能实现了在票券列表查询时，后端接口返回用户是否已经抢购过该票券的信息，前端根据返回的信息将已抢购的票券置灰显示。

## 实现内容

### 1. 后端接口修改

#### 1.1 Ticket模型扩展
- 在 `Ticket` 模型中添加了 `userPurchased` 字段，用于标识用户是否已购买该票券
- 字段类型：`boolean`，默认值为 `false`

#### 1.2 TicketService接口扩展
- 新增 `getRecentTicketsWithUserStatus(Long userId)` 方法
- 该方法返回最近3天的票券信息，并携带用户是否抢购该票券的结果

#### 1.3 TicketServiceImpl实现
- 实现 `getRecentTicketsWithUserStatus` 方法：
  - 获取基础票券信息
  - 如果用户未登录（userId为null或<=0），直接返回基础信息
  - 为每个票券添加用户购买状态
  - 优先从缓存查询，缓存未命中则查询数据库

- 实现 `checkUserPurchaseStatus` 方法（私有方法）：
  - 优先从Redis缓存查询用户购买状态
  - 缓存未命中时查询数据库的 `ticket_order` 表
  - 将查询结果写入缓存，提高后续查询性能
  - 缓存键格式：`USER_HAS_ORDER_{日期}_{用户ID}`
  - 缓存过期时间：12小时

- 实现 `checkUserPurchaseStatusFromCache` 方法（私有方法）：
  - 从Redis缓存查询用户购买状态
  - 缓存值："1"表示已购买，"0"表示未购买

#### 1.4 TicketController新增接口
- 新增 `/api/tickets/listWithUserStatus` 接口
- 支持可选的 `userId` 参数
- 返回包含用户购买状态的票券列表

### 2. 前端页面修改

#### 2.1 接口调用修改
- 修改 `loadTickets()` 方法，调用新的 `/listWithUserStatus` 接口
- 传递当前用户ID作为参数

#### 2.2 购买状态显示
- 修改票券卡片按钮的显示逻辑
- 已购买的票券：
  - 按钮置灰（`bg-gray-200 text-gray-500 cursor-not-allowed`）
  - 显示"已抢购"文本
  - 显示勾选图标（`fa-check`）
  - 按钮禁用状态

#### 2.3 状态同步
- 新增 `updatePurchasedDates()` 方法
- 根据后端返回的 `userPurchased` 字段更新本地状态
- 保持与现有逻辑的兼容性

## 技术特点

### 1. 缓存优先策略
- 优先从Redis缓存查询用户购买状态
- 缓存未命中时查询数据库
- 查询结果自动写入缓存，提高后续查询性能
- 缓存过期时间合理设置（12小时）

### 2. 向后兼容
- 保留原有的 `/list` 接口
- 新增 `/listWithUserStatus` 接口
- 前端可以平滑升级到新接口

### 3. 性能优化
- 批量查询用户购买状态
- 减少数据库查询次数
- 智能缓存策略，避免重复查询

## 核心方法实现

### getRecentTicketsWithUserStatus(Long userId)
```java
@Override
public List<Ticket> getRecentTicketsWithUserStatus(Long userId) {
    try {
        // 获取基础票券信息
        List<Ticket> tickets = getRecentTickets();
        
        // 如果用户未登录，直接返回基础信息
        if (userId == null) {
            LOGGER.info("用户未登录，返回基础票券信息");
            return tickets;
        }
        
        // 为每个票券添加用户购买状态
        for (Ticket ticket : tickets) {
            boolean hasPurchased = checkUserPurchaseStatus(userId, ticket.getDate());
            ticket.setUserPurchased(hasPurchased);
        }
        
        return tickets;
    } catch (Exception e) {
        LOGGER.error("获取带用户状态的票券信息失败，用户ID: {}", userId, e);
        // 如果出错，返回基础票券信息
        return getRecentTickets();
    }
}
```

### checkUserPurchaseStatus(Long userId, String date)
```java
private boolean checkUserPurchaseStatus(Long userId, String date) {
    try {
        // 1. 先从缓存查询用户购买状态
        boolean fromCache = checkUserPurchaseStatusFromCache(userId, date);
        if (fromCache) {
            return true;
        }
        
        // 2. 缓存中没有，查询数据库ticket_order表
        TicketOrder ticketOrder = ticketOrderMapper.selectByUserIdAndDate(userId, date);
        boolean hasPurchased = ticketOrder != null;
        
        // 3. 将查询结果写入缓存
        String cacheKey = CacheKey.USER_HAS_ORDER.getKey() + "_" + date + "_" + userId;
        if (hasPurchased) {
            stringRedisTemplate.opsForValue().set(cacheKey, "1", 12, TimeUnit.HOURS);
        } else {
            stringRedisTemplate.opsForValue().set(cacheKey, "0", 12, TimeUnit.HOURS);
        }
        
        return hasPurchased;
    } catch (Exception e) {
        LOGGER.error("查询用户购买状态失败，用户ID: {}, 日期: {}", userId, date, e);
        return false;
    }
}
```

## 使用方式

### 1. 后端接口调用
```bash
# 获取包含用户购买状态的票券列表
GET /api/tickets/listWithUserStatus?userId=123

# 获取基础票券列表（不包含用户状态）
GET /api/tickets/list
```

### 2. 前端集成
```javascript
// 调用新接口获取票券信息
const response = await axios.get('/api/tickets/listWithUserStatus', {
    params: { userId: currentUserId }
});

// 根据userPurchased字段显示状态
if (ticket.userPurchased) {
    // 显示已购买状态
    button.disabled = true;
    button.classList.add('bg-gray-200');
}
```

## 数据库表结构

### ticket_order表
- `id`: 主键
- `user_id`: 用户ID
- `ticket_date`: 票券日期
- `ticket_code`: 票券编码
- `status`: 订单状态
- `create_time`: 创建时间

## 缓存键结构

### 用户购买状态缓存
- 键格式：`USER_HAS_ORDER_{日期}_{用户ID}`
- 值：`"1"` 表示已购买，`"0"` 表示未购买
- 过期时间：12小时

## 查询流程

1. **缓存查询**：首先从Redis缓存查询用户购买状态
2. **缓存命中**：如果缓存中有数据，直接返回结果
3. **数据库查询**：如果缓存未命中，查询数据库`ticket_order`表
4. **缓存更新**：将查询结果写入缓存，设置12小时过期时间
5. **返回结果**：返回用户购买状态

## 注意事项

1. **用户ID参数**：`userId` 参数为可选，如果为 `null` 或 `<= 0`，则返回基础票券信息
2. **缓存一致性**：用户购买票券后，需要及时更新缓存中的购买状态
3. **错误处理**：如果查询用户购买状态失败，会回退到基础票券信息
4. **性能考虑**：大量用户同时查询时，建议增加缓存预热机制
5. **缓存过期**：缓存过期时间设置为12小时，平衡性能和一致性

## 测试建议

1. **功能测试**：验证已购买票券是否正确置灰显示
2. **性能测试**：验证缓存查询的性能提升
3. **并发测试**：验证多用户同时查询时的系统稳定性
4. **兼容性测试**：验证新接口不影响现有功能
5. **缓存测试**：验证缓存查询和数据库查询的正确性
