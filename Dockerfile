# 构建阶段：使用 Maven 编译
FROM maven:3.6.3-jdk-8-slim AS builder
WORKDIR /app

# 先复制 pom 文件，利用 Docker 缓存加速依赖下载
COPY pom.xml .
COPY miaosha-dao/pom.xml miaosha-dao/
COPY miaosha-service/pom.xml miaosha-service/
COPY miaosha-web/pom.xml miaosha-web/
RUN mvn dependency:go-offline -B

# 复制源码并构建
COPY miaosha-dao/src miaosha-dao/src
COPY miaosha-service/src miaosha-service/src
COPY miaosha-web/src miaosha-web/src
RUN mvn clean package -DskipTests

# 运行阶段：仅包含 JRE
FROM openjdk:8-jre-slim
WORKDIR /app
COPY --from=builder /app/miaosha-web/target/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
