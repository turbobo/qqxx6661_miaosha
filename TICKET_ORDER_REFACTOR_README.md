# 票券订单系统重构说明

## 重构概述

本次重构简化了票券购票流程，删除了复杂的中间环节，直接使用票券订单表来管理购票记录。重构后的系统更加简洁、高效，减少了数据冗余和复杂性。

## 重构内容

### 1. 删除的方法

- `createPurchaseRecord()` - 创建购票记录方法
- `createOrderWithTransaction()` - 事务控制创建订单方法  
- `updatePurchaseRecordAfterOrderCreation()` - 更新购票记录方法

### 2. 新增的组件

#### 2.1 票券订单实体类 (`TicketOrder`)
```java
public class TicketOrder {
    private Integer id;           // 主键ID
    private String orderNo;       // 订单编号
    private Long userId;          // 用户ID
    private Integer ticketId;     // 票券ID
    private String ticketCode;    // 票券编码
    private String ticketDate;    // 购票日期
    private Integer status;       // 订单状态
    private Long amount;          // 订单金额（分）
    private Date payTime;         // 支付时间
    private Date createTime;      // 创建时间
    private Date updateTime;      // 更新时间
    private String remark;        // 备注
}
```

#### 2.2 票券订单Mapper接口 (`TicketOrderMapper`)
- 基础的CRUD操作
- 按用户ID、票券ID、状态等条件查询
- 支持订单状态更新

#### 2.3 票券订单服务接口 (`TicketOrderService`)
- 创建票券订单
- 查询票券订单
- 更新订单状态
- 支付和取消订单
- 删除订单

#### 2.4 票券订单服务实现 (`TicketOrderServiceImpl`)
- 完整的业务逻辑实现
- 事务控制
- 异常处理
- 日志记录

### 3. 数据库表结构

```sql
CREATE TABLE `ticket_order` (
  `id` int(11) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `order_no` varchar(64) NOT NULL COMMENT '订单编号',
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `ticket_id` int(11) NOT NULL COMMENT '票券ID',
  `ticket_code` varchar(128) NOT NULL COMMENT '票券编码',
  `ticket_date` varchar(20) NOT NULL COMMENT '购票日期',
  `status` int(11) NOT NULL DEFAULT '1' COMMENT '订单状态：1-待支付，2-已支付，3-已取消，4-已过期',
  `amount` bigint(20) NOT NULL COMMENT '订单金额（分）',
  `pay_time` datetime DEFAULT NULL COMMENT '支付时间',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  `update_time` datetime NOT NULL COMMENT '更新时间',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_ticket_id` (`ticket_id`),
  KEY `idx_ticket_code` (`ticket_code`),
  KEY `idx_status` (`status`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='票券订单表';
```

## 重构后的购票流程

### 原始流程（复杂）
```
1. 悲观锁扣减库存
2. 生成票券编码
3. 创建购票记录
4. 调用订单服务创建订单
5. 更新购票记录的订单ID和票券ID
6. 更新缓存
7. 返回结果
```

### 重构后流程（简化）
```
1. 悲观锁扣减库存
2. 生成票券编码
3. 创建票券订单
4. 更新缓存
5. 返回结果
```

## 技术特点

### 1. 数据一致性
- 使用悲观锁确保库存扣减的原子性
- 事务控制保证数据一致性
- 订单创建失败时自动回滚

### 2. 性能优化
- 减少了数据库操作次数
- 简化了业务流程
- 降低了系统复杂度

### 3. 可维护性
- 清晰的代码结构
- 完善的异常处理
- 详细的日志记录

### 4. 扩展性
- 支持多种订单状态
- 灵活的查询接口
- 易于添加新功能

## 订单状态管理

### 状态定义
- **1 - 待支付**：订单创建后的初始状态
- **2 - 已支付**：用户完成支付后的状态
- **3 - 已取消**：订单被取消的状态
- **4 - 已过期**：订单超时未支付的状态

### 状态流转
```
待支付 → 已支付（支付成功）
待支付 → 已取消（用户取消）
待支付 → 已过期（超时未支付）
```

## 订单编号生成

### 生成规则
```
TO + 时间戳 + 4位随机数
```

### 示例
- `TO1705123456789001` - 2025年1月15日创建的订单
- `TO1705123456789002` - 同一时间创建的另一个订单

## 测试覆盖

### 单元测试
- 订单创建测试
- 订单查询测试
- 状态更新测试
- 支付流程测试
- 取消流程测试
- 删除操作测试

### 测试场景
- 正常流程测试
- 异常流程测试
- 边界条件测试
- 并发安全测试

## 部署说明

### 1. 数据库变更
```sql
-- 执行票券订单表创建脚本
source ticket_order_table.sql;
```

### 2. 应用配置
- 确保Redis配置正确
- 检查数据库连接配置
- 验证MyBatis映射文件路径

### 3. 服务启动
- 启动Redis服务
- 启动应用服务
- 检查日志输出

## 监控和运维

### 1. 关键指标
- 订单创建成功率
- 订单状态转换成功率
- 数据库操作响应时间
- 系统错误率

### 2. 日志监控
- 订单创建日志
- 状态变更日志
- 异常错误日志
- 性能监控日志

### 3. 告警配置
- 订单创建失败告警
- 数据库连接异常告警
- 系统性能下降告警

## 故障排查

### 1. 常见问题
- 订单创建失败
- 状态更新异常
- 数据库连接问题
- 性能下降

### 2. 排查步骤
1. 检查应用日志
2. 验证数据库连接
3. 检查Redis状态
4. 分析性能指标
5. 查看错误堆栈

## 后续优化建议

### 1. 功能增强
- 添加订单超时自动取消
- 支持订单批量操作
- 增加订单统计报表
- 实现订单搜索功能

### 2. 性能优化
- 添加订单缓存
- 优化数据库查询
- 实现异步处理
- 支持分库分表

### 3. 监控完善
- 集成Prometheus监控
- 添加Grafana面板
- 配置告警规则
- 实现链路追踪

## 总结

本次重构成功简化了票券购票系统，主要优势包括：

1. **流程简化**：减少了中间环节，提高了系统效率
2. **数据统一**：使用票券订单表统一管理购票信息
3. **维护性提升**：代码结构更清晰，易于维护和扩展
4. **性能优化**：减少了数据库操作，提升了系统性能
5. **可靠性增强**：完善的事务控制和异常处理

重构后的系统更加健壮、高效，为后续功能扩展奠定了良好基础。
