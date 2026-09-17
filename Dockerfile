# 构建阶段：使用 Maven 编译
# 基础镜像选用 eclipse-temurin 系（多架构，Apple Silicon 原生 arm64 运行，无需 Rosetta 模拟）
FROM maven:3.8-eclipse-temurin-8 AS builder
WORKDIR /app

# 配置阿里云公共镜像源，加速容器内 Maven 依赖下载（公网镜像，非内网）
RUN mkdir -p /root/.m2 \
    && printf '%s\n' \
    '<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0">' \
    '  <mirrors>' \
    '    <mirror>' \
    '      <id>aliyun-public</id>' \
    '      <name>Aliyun Public Repository</name>' \
    '      <url>https://maven.aliyun.com/repository/public</url>' \
    '      <mirrorOf>central</mirrorOf>' \
    '    </mirror>' \
    '  </mirrors>' \
    '</settings>' > /root/.m2/settings.xml

# 先复制 pom 文件，预热第三方依赖到镜像层（pom 未变时下次构建直接命中层缓存，无需重新下载）
# 说明：-fn（fail-never）容错——多模块 reactor 下 go-offline 无法解析尚未构建的兄弟模块
# （如 miaosha-service 依赖 miaosha-dao.jar），-fn 会继续预热其余模块；
# 这部分 reactor 内部依赖无需预下载，由后续 package 阶段的 reactor 构建补齐
COPY pom.xml .
COPY miaosha-dao/pom.xml miaosha-dao/
COPY miaosha-service/pom.xml miaosha-service/
COPY miaosha-web/pom.xml miaosha-web/
RUN mvn -B -fn dependency:go-offline -Dmaven.test.skip=true

# 复制源码并构建（-Dmaven.test.skip=true：镜像构建跳过测试编译与执行）
COPY miaosha-dao/src miaosha-dao/src
COPY miaosha-service/src miaosha-service/src
COPY miaosha-web/src miaosha-web/src
RUN mvn -B clean package -Dmaven.test.skip=true

# 运行阶段：仅包含 JRE
FROM eclipse-temurin:8-jre
WORKDIR /app
COPY --from=builder /app/miaosha-web/target/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
