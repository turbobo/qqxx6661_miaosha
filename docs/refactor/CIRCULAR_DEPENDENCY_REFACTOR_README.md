# 循环依赖重构说明

## 问题描述

在原有的代码结构中，`TicketServiceImpl` 和 `UserServiceImpl` 之间存在循环依赖：

- `TicketServiceImpl` 依赖 `UserService`
- `UserServiceImpl` 依赖 `TicketService`

这种循环依赖会导致：
1. Spring容器启动问题
2. 内存泄漏风险
3. 代码耦合度过高
4. 维护困难

## 解决方案

采用**重构代码结构**的方式，将共同的验证逻辑提取到新的 `ValidationService` 中：

### 重构前
```
TicketServiceImpl ←→ UserServiceImpl
       ↑               ↑
       └───────┐       │
               └───────┘
```

### 重构后
```
TicketServiceImpl     UserServiceImpl
       ↑                    ↑
       └─── ValidationService
```

## 重构内容

### 1. 新增 ValidationService 接口

```java
public interface ValidationService {
    // 票数验证
    Map<String, Object> validateTicketCount(String date);
    void validateTicketCountWithException(String date);
    
    // 用户验证
    Map<String, Object> validateUser(Long userId);
    void validateUserWithException(Long userId);
    
    // 抢购时间验证
    Map<String, Object> validatePurchaseTime(String date);
    void validatePurchaseTimeWithException(String date);
    
    // 综合验证
    Map<String, Object> validatePurchaseRequest(Long userId, String date);
}
```

### 2. 新增 ValidationServiceImpl 实现类

- 集中处理所有验证逻辑
- 避免循环依赖
- 提供统一的验证接口

### 3. 修改 TicketServiceImpl

**移除依赖：**
```java
// 移除
@Resource
private UserService userService;

// 添加
@Resource
private ValidationService validationService;
```

**修改方法调用：**
```java
// 原来
userService.validUser(request.getUserId());

// 现在
validationService.validateUserWithException(request.getUserId());
```

### 4. 修改 UserServiceImpl

**移除依赖：**
```java
// 移除
@Resource
private TicketService ticketService;

// 添加
@Resource
private ValidationService validationService;
```

**修改方法调用：**
```java
// 原来
ticketService.validateTicketCount(date);

// 现在
validationService.validateTicketCountWithException(date);
```

## 重构优势

### 1. 消除循环依赖
- 两个服务不再直接依赖对方
- Spring容器可以正常启动
- 避免了内存泄漏风险

### 2. 提高代码质量
- 验证逻辑集中管理
- 代码职责更加清晰
- 便于单元测试

### 3. 增强可维护性
- 验证规则统一修改
- 减少代码重复
- 提高代码复用性

### 4. 符合设计原则
- 单一职责原则：每个服务只负责自己的业务逻辑
- 依赖倒置原则：依赖抽象而非具体实现
- 开闭原则：易于扩展新的验证规则

## 使用方式

### 在 TicketServiceImpl 中
```java
@Resource
private ValidationService validationService;

public void someMethod() {
    // 验证票数
    validationService.validateTicketCountWithException(date);
    
    // 验证用户
    validationService.validateUserWithException(userId);
    
    // 验证抢购时间
    validationService.validatePurchaseTimeWithException(date);
}
```

### 在 UserServiceImpl 中
```java
@Resource
private ValidationService validationService;

public void someMethod() {
    // 验证票数
    validationService.validateTicketCountWithException(date);
    
    // 验证用户
    validationService.validateUserWithException(userId);
}
```

## 注意事项

1. **接口设计**：ValidationService 提供了两种验证方法：
   - 返回验证结果的方法（用于需要处理验证结果的场景）
   - 抛出异常的方法（用于快速失败的场景）

2. **异常处理**：验证失败时会抛出 RuntimeException，调用方需要适当处理

3. **日志记录**：ValidationService 中包含了详细的日志记录，便于问题排查

4. **性能考虑**：验证逻辑相对简单，性能影响很小

## 总结

通过这次重构，我们成功解决了循环依赖问题，同时提高了代码质量和可维护性。这种设计模式可以作为解决类似循环依赖问题的参考方案。

