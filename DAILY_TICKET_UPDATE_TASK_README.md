# 每日票券更新定时任务说明

## 功能概述

本系统实现了每日票券数据更新的定时任务，每天凌晨1点自动执行，主要功能包括：

1. **清除今天的票数** - 删除过期的票券记录
2. **保留明天、后天的票数** - 保持现有数据不变
3. **更新后天的票数** - 根据配置更新票数信息
4. **清理相关缓存** - 确保缓存与数据库数据一致

## 核心特性

### 1. 定时执行
- 使用Spring的`@Scheduled`注解
- 默认每天凌晨1点执行（可配置）
- 支持cron表达式自定义执行时间

### 2. 事务控制
- 使用`@Transactional`注解确保数据一致性
- 异常时自动回滚，保证数据完整性
- 支持手动触发执行

### 3. 配置化管理
- 支持通过配置文件调整参数
- 可动态启用/禁用定时任务
- 票数信息完全可配置

## 使用方法

### 1. 自动执行
定时任务会在每天凌晨1点自动执行，无需手动干预。

### 2. 手动触发
```java
@Resource
private DailyTicketUpdateTask dailyTicketUpdateTask;

// 手动执行定时任务
dailyTicketUpdateTask.manualUpdateDailyTickets();
```

### 3. 配置参数
在`application-schedule.properties`中配置相关参数：

```properties
schedule:
  ticket:
    # 执行时间（每天凌晨1点）
    daily-ticket-update-cron: "0 0 1 * * ?"
    
    # 后天票券的新总票数
    day-after-tomorrow-total-count: 100
    
    # 后天票券的新剩余票数
    day-after-tomorrow-remaining-count: 100
    
    # 后天票券的新已售票数
    day-after-tomorrow-sold-count: 0
    
    # 后天票券的名称
    day-after-tomorrow-name: "dayAfterTomorrow"
    
    # 是否启用定时任务
    enabled: true
```

## 技术实现

### 1. 任务执行流程

```java
@Scheduled(cron = "${schedule.ticket.daily-ticket-update-cron:0 0 1 * * ?}")
@Transactional(rollbackFor = Exception.class)
public void updateDailyTickets() {
    // 1. 检查是否启用
    // 2. 获取日期范围
    // 3. 清除今天的票数
    // 4. 保留明天、后天的票数
    // 5. 更新后天的票数
    // 6. 清理相关缓存
}
```

### 2. 核心方法

#### 清除今天的票数
```java
private void clearTodayTickets(String todayStr) {
    // 查询今天的票券记录
    // 删除记录
    // 记录操作日志
}
```

#### 更新后天的票数
```java
private void updateDayAfterTomorrowTickets(String dayAfterTomorrowStr) {
    // 查询后天的票券记录
    // 如果存在则更新，不存在则创建
    // 使用配置中的票数信息
}
```

#### 清理相关缓存
```java
private void cleanRelatedCache(String todayStr, String tomorrowStr, String dayAfterTomorrowStr) {
    // 删除相关日期的缓存
    // 更新票券列表缓存
}
```

### 3. 配置类结构

```java
@Data
@Configuration
@ConfigurationProperties(prefix = "schedule.ticket")
public class ScheduleConfig {
    private String dailyTicketUpdateCron;
    private Integer dayAfterTomorrowTotalCount;
    private Integer dayAfterTomorrowRemainingCount;
    private Integer dayAfterTomorrowSoldCount;
    private String dayAfterTomorrowName;
    private Boolean enabled;
    private Long timeout;
}
```

## 数据库操作

### 1. 删除操作
```sql
DELETE FROM ticket WHERE date = '2025-08-12';
```

### 2. 更新操作
```sql
UPDATE ticket SET 
    total_count = 100,
    remaining_count = 100,
    sold_count = 0,
    name = 'dayAfterTomorrow',
    version = version + 1,
    update_time = NOW()
WHERE date = '2025-08-14';
```

### 3. 插入操作
```sql
INSERT INTO ticket (date, name, total_count, remaining_count, sold_count, version, status, create_time, update_time)
VALUES ('2025-08-14', 'dayAfterTomorrow', 100, 100, 0, 1, 1, NOW(), NOW());
```

## 缓存管理

### 1. 缓存清理
- 删除今天、明天、后天的票券缓存
- 更新票券列表缓存
- 确保缓存与数据库数据一致

### 2. 缓存策略
- 先清理缓存，再更新数据库
- 缓存操作失败不影响主流程
- 支持缓存预热和更新

## 异常处理

### 1. 事务回滚
- 任何异常发生时自动回滚事务
- 确保数据一致性
- 记录详细的错误日志

### 2. 容错机制
- 缓存操作失败不影响主流程
- 数据库操作失败时抛出异常
- 支持手动重试执行

## 监控和日志

### 1. 日志记录
- 任务开始和结束日志
- 每个步骤的详细日志
- 错误和异常日志

### 2. 监控指标
- 任务执行时间
- 成功/失败次数
- 数据库操作影响行数

## 测试支持

### 1. 单元测试
- 使用Mockito进行单元测试
- 覆盖各种业务场景
- 验证方法调用和返回值

### 2. 测试场景
- 任务启用/禁用测试
- 创建新票券记录测试
- 更新现有票券记录测试
- 异常情况处理测试

## 部署和运维

### 1. 环境配置
- 开发环境：可调整执行时间
- 测试环境：支持手动触发
- 生产环境：固定凌晨1点执行

### 2. 监控告警
- 任务执行失败告警
- 执行时间超时告警
- 数据库操作异常告警

### 3. 备份策略
- 执行前备份相关数据
- 支持数据回滚操作
- 定期备份票券数据

## 扩展建议

### 1. 功能扩展
- 支持更多日期的票券管理
- 添加票券类型和分类
- 支持动态票数计算

### 2. 性能优化
- 批量操作优化
- 异步处理支持
- 分布式任务调度

### 3. 监控完善
- 添加Prometheus指标
- 集成ELK日志系统
- 实时监控面板

## 注意事项

1. **时间设置**: 确保服务器时间准确，避免时区问题
2. **数据库连接**: 确保数据库连接池配置合理
3. **事务超时**: 设置合理的事务超时时间
4. **并发控制**: 避免多个实例同时执行任务
5. **数据备份**: 执行前备份重要数据

## 故障排查

### 1. 常见问题
- 任务未执行：检查cron表达式和启用状态
- 执行失败：查看错误日志和数据库状态
- 数据不一致：检查缓存和数据库同步

### 2. 排查步骤
1. 检查应用日志
2. 验证数据库连接
3. 确认配置参数
4. 测试手动执行
5. 检查系统时间
