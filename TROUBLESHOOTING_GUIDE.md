# 故障排除指南：TicketEntityMapper 为空问题

## 问题描述

在启动应用时，`TicketEntityMapper ticketEntityMapper` 依赖注入失败，导致该字段为 `null`。

## 可能的原因

### 1. MyBatis Mapper 扫描失败
- `@MapperScan` 注解配置错误
- Mapper XML 文件路径配置错误
- 类路径中找不到 Mapper 接口

### 2. 模块依赖问题
- `miaosha-service` 模块没有正确依赖 `miaosha-dao` 模块
- Maven 依赖版本不匹配

### 3. Spring 配置问题
- Bean 扫描范围不正确
- 组件注解缺失

## 解决方案

### 方案1：检查依赖注入状态

启动应用后，访问以下接口检查依赖注入状态：

```bash
# 检查所有依赖注入状态
GET /api/test/dependency

# 单独检查数据库连接
GET /api/test/database

# 单独检查Redis连接
GET /api/test/redis
```

### 方案2：检查应用启动日志

查看应用启动日志，特别关注以下信息：

1. **MyBatis 初始化日志**：
   ```
   INFO  - MyBatis configuration initialized
   INFO  - Mapped SQL statement found
   ```

2. **依赖注入日志**：
   ```
   INFO  - Dependency injection check completed
   INFO  - ✓ TicketEntityMapper 注入成功
   ```

3. **错误日志**：
   ```
   ERROR - TicketEntityMapper 依赖注入失败
   ERROR - Failed to create bean
   ```

### 方案3：验证 Mapper 接口

确保 `TicketEntityMapper` 接口正确配置：

```java
package cn.monitor4all.miaoshadao.mapper;

import org.apache.ibatis.annotations.Mapper;

@Mapper  // 确保有这个注解
public interface TicketEntityMapper {
    // ... 方法定义
}
```

### 方案4：检查 Mapper XML 文件

确保 Mapper XML 文件存在且路径正确：

1. 文件位置：`miaosha-dao/src/main/resources/mapper/TicketEntityMapper.xml`
2. 命名空间：`cn.monitor4all.miaoshadao.mapper.TicketEntityMapper`
3. 方法映射：确保所有接口方法都有对应的 SQL 映射

### 方案5：验证 MyBatis 配置

检查 `application.properties` 配置：

```properties
# MyBatis配置
mybatis.mapper-locations=classpath*:mapper/*.xml
mybatis.type-aliases-package=cn.monitor4all.miaoshadao.dao

# 启用MyBatis日志
logging.level.cn.monitor4all.miaoshadao.mapper=DEBUG
```

### 方案6：检查模块依赖

确保 `miaosha-service/pom.xml` 包含：

```xml
<dependency>
    <groupId>cn.monitor4all</groupId>
    <artifactId>miaosha-dao</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

## 调试步骤

### 步骤1：启动应用
```bash
mvn clean compile
mvn spring-boot:run
```

### 步骤2：检查启动日志
观察控制台输出，查找错误信息。

### 步骤3：访问测试接口
```bash
curl http://localhost:8081/api/test/dependency
```

### 步骤4：分析响应结果
根据接口返回的依赖注入状态，定位具体问题。

## 常见错误及解决方案

### 错误1：No qualifying bean of type 'TicketEntityMapper'
**原因**：MyBatis 没有扫描到 Mapper 接口
**解决**：检查 `@MapperScan` 注解和包路径配置

### 错误2：Invalid bound statement (not found)
**原因**：Mapper XML 文件路径错误或内容错误
**解决**：检查 XML 文件路径和 SQL 映射配置

### 错误3：Bean creation failed
**原因**：依赖注入循环或配置错误
**解决**：检查 Bean 依赖关系和注解配置

## 预防措施

### 1. 使用启动检查服务
`StartupCheckService` 会在应用启动后自动检查依赖注入状态。

### 2. 添加日志监控
启用 DEBUG 级别的 MyBatis 日志，便于排查问题。

### 3. 单元测试
为 Mapper 接口编写单元测试，确保基本功能正常。

## 联系支持

如果问题仍然存在，请提供以下信息：

1. 完整的错误日志
2. 应用启动日志
3. 依赖注入测试接口的响应
4. 项目结构和配置文件

## 总结

`TicketEntityMapper` 为空通常是由于 MyBatis 配置问题或模块依赖问题导致的。通过以上步骤，可以逐步排查和解决问题。建议按照调试步骤逐一检查，确保每个环节都配置正确。
