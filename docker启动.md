# 博物馆秒杀项目 · 本地开发指南

> 项目：`qqxx6661_miaosha`  
> 技术栈：Spring Boot 2.2.5 + Java 8 + MySQL 8 + Redis 6 + RabbitMQ 3  
> 运行方式：Docker 跑中间件，应用本地 IDEA 启动（**最贴近线上环境、改动可即时生效**）

---

## 📌 日常开发（每天开工）

**只要做两件事：**

```bash
# 1. 起 Docker 环境 + 三个中间件（约 30 秒）
colima start
cd /Users/qinghang/Documents/github/turbobo/qqxx6661_miaosha
docker compose up -d mysql redis rabbitmq

# 2. IDEA 里 Run `MiaoshaWebApplication`（首次构建见下文）
```

服务起来后访问：
- 后端 API：`http://localhost:8081`
- RabbitMQ 管理台：`http://localhost:15672` （guest / guest）
- 查数据：`docker exec -it miaosha-mysql mysql -uroot -proot m4a_miaosha`

**收工时**：
```bash
docker compose stop      # 停容器（数据保留在 volume 里，下次启动还在）
colima stop              # 释放内存
```

---

## 🏗️ 一次性首次搭建（只在重装环境时做一次）

### Step 1 · 安装 Colima + Docker CLI

```bash
# 配清华镜像加速（国内必做，否则 brew 下载很慢）
export HOMEBREW_API_DOMAIN="https://mirrors.tuna.tsinghua.edu.cn/homebrew-bottles/api"
export HOMEBREW_BOTTLE_DOMAIN="https://mirrors.tuna.tsinghua.edu.cn/homebrew-bottles"
export HOMEBREW_NO_AUTO_UPDATE=1

brew install colima docker docker-compose
```

> 不推荐装 Docker Desktop——安装包 1.5GB 且国内下载很慢（约 46KB/s ≈ 9 小时），  
> 而且对阿里这种规模企业要付费订阅。colima 完全免费、体积小、CLI 兼容。

### Step 2 · 配 Docker Hub 镜像加速器（解决拉镜像慢）

编辑 `~/.colima/default/colima.yaml`，在末尾加上：

```yaml
docker:
  registry-mirrors:
    - https://docker.1panel.live
```

> 实测 1panel 可用、速度快；daocloud 返回 401、imgdb 数据校验失败，已淘汰。

### Step 3 · 启动 colima

```bash
colima start --cpu 4 --memory 8 --disk 60 --vm-type vz --mount-type virtiofs
```

首次会拉 300+MB 的 Linux 镜像，用 `aria2c -x16` 多线程可压到几分钟（单连接 40KB/s，16 连接 264KB/s）。

### Step 4 · 起中间件

```bash
cd /Users/qinghang/Documents/github/turbobo/qqxx6661_miaosha
docker compose up -d mysql redis rabbitmq
docker compose ps                # 等 STATUS 都变成 healthy
```

> ⚠️ **不要** `docker compose up -d`（不带服务名），那样会把 `app` 也拉起来  
> 在容器内重新构建 Maven，网络极慢，而且会和 IDEA 本地实例抢 8081 端口。

### Step 5 · 初始化数据库

`docker-compose.yml` 只挂载了 `miaosha.sql`（基础表），票券相关的表需要补充：

```bash
docker exec -i miaosha-mysql mysql -uroot -proot m4a_miaosha < init_ticket_database.sql
docker exec -i miaosha-mysql mysql -uroot -proot m4a_miaosha < ticket_order_table.sql
```

验证：
```bash
docker exec miaosha-mysql mysql -uroot -proot -e "use m4a_miaosha; show tables; select * from ticket;"
```

应该看到 3 条 `CURDATE()` 生成的今明后天票券。

### Step 6 · 配 Maven 阿里云镜像

`~/.m2/settings.xml` 关键片段：

```xml
<mirrors>
  <mirror>
    <id>aliyun-public</id>
    <name>Aliyun Public Repository</name>
    <url>https://maven.aliyun.com/repository/public</url>
    <mirrorOf>central</mirrorOf>
  </mirror>
</mirrors>
```

不配这个，构建走 Maven 中央仓库，国内会卡很久。

### Step 7 · 构建

```bash
export PATH="/Users/qinghang/Documents/develop/apache-maven-3.9.11/bin:$PATH"
mvn clean install -DskipTests
```

> **必须用 `install` 不是 `package`**——多模块项目，`miaosha-web` 依赖 `miaosha-dao` / `miaosha-service`，  
> 只有 install 才会把它们装进本地仓库供 web 模块引用。

产物：`miaosha-web/target/miaosha-web-1.0.0-SNAPSHOT.jar`（39MB，可执行）。

---

## 🚀 启动应用

### 方式 A · IDEA（推荐，调试/热部署方便）

1. **Project SDK**：`File → Project Structure`，选 **JDK 1.8**（机器上是 `openjdk 1.8.0_472`）
2. **启动类**：**`miaosha-web` 模块下的 `MiaoshaWebApplication`**
   > ⚠️ 项目里有三个 `@SpringBootApplication`（dao / service / web 各一个），**只有 web 模块的才对**
3. **Profile**：保持默认，**不要激活 `dev`**（`dev` 用 H2 内存库，会绕过你刚起的 MySQL）
4. 点 Run → 控制台看到 `Started MiaoshaWebApplication in X seconds` 即成功

### 方式 B · 命令行

```bash
java -jar miaosha-web/target/miaosha-web-1.0.0-SNAPSHOT.jar
```

服务起来后，浏览器访问 `http://localhost:8081`。

---

## 🔍 常用排查命令

```bash
# 容器状态
docker compose ps
docker compose logs -f mysql        # 实时看 MySQL 日志

# 看数据
docker exec miaosha-mysql mysql -uroot -proot -t -e \
  "use m4a_miaosha; select * from ticket;"

# 看订单
docker exec miaosha-mysql mysql -uroot -proot -t -e \
  "use m4a_miaosha; select * from ticket_order order by create_time desc;"

# 重置票券余量（压测 / 联调时常用）
docker exec miaosha-mysql mysql -uroot -proot -e \
  "use m4a_miaosha; update ticket set remaining_count = total_count, sold_count = 0;"

# 清空订单和抢购记录（慎用）
docker exec miaosha-mysql mysql -uroot -proot -e \
  "use m4a_miaosha; delete from ticket_order; delete from ticket_purchase_record;"

# Redis 缓存查看
docker exec -it miaosha-redis redis-cli
# > keys *
# > get <key>
```

---

## ⚠️ 常见问题

| 现象 | 原因 | 解决 |
|---|---|---|
| IDEA 启动报 `Connection refused` 到 3306 / 6379 / 5672 | 中间件没起来 | `docker compose ps` 看 status |
| IDEA 启动报 `Table doesn't exist` | 数据库没初始化 | 补跑 Step 5 的两个 SQL |
| `mvn install` 报找不到 `miaosha-service` / `miaosha-dao` | 没用 install | 重新 `mvn clean install -DskipTests` |
| `docker compose ps` 一直 `starting` | healthcheck 没过 | `docker compose logs mysql` 看具体错 |
| 端口 8081 已被占用 | 上次 jar 进程没关 | `lsof -i :8081` 找到 PID 后 `kill` |
| colima 启动失败 / 镜像下载慢 | 网络问题 | 看 `~/.colima/default/colima.yaml` 的 registry-mirrors 是否配好 |
| 前端页面打不开 | 静态资源在 `resources` 根目录 | 直接浏览器打开 `miaosha-web/src/main/resources/index.html`，后端 8081 提供 API |

---

## 📁 关键路径

| 用途 | 路径 |
|---|---|
| 项目根目录 | `/Users/qinghang/Documents/github/turbobo/qqxx6661_miaosha` |
| 启动主类 | `miaosha-web/src/main/java/cn/monitor4all/miaoshaweb/MiaoshaWebApplication.java` |
| 业务配置 | `miaosha-web/src/main/resources/application.properties` |
| 服务配置（含 Redis） | `miaosha-service/src/main/resources/application.properties` |
| 前端页面 | `miaosha-web/src/main/resources/*.html` |
| 数据库初始化 | `miaosha.sql` + `init_ticket_database.sql` + `ticket_order_table.sql` |
| Maven 本地仓库 | `~/.m2/repository`（已有 205MB 缓存） |
| Maven 安装位置 | `/Users/qinghang/Documents/develop/apache-maven-3.9.11` |
| JDK 8 | 系统自带 `openjdk 1.8.0_472` |

---

## 🗂️ 架构速览

```
┌─────────────────────────────────────────────────────────┐
│  宿主机 macOS                                            │
│                                                          │
│  ┌─────────────────────────┐   ┌──────────────────────┐ │
│  │ colima VM (Ubuntu 24.04)│   │ IDEA / 命令行        │ │
│  │                         │   │                      │ │
│  │  ┌─────────┐ ┌───────┐  │   │  ┌────────────────┐  │ │
│  │  │ MySQL 8 │ │ Redis │  │   │  │ miaosha-web    │  │ │
│  │  │ :3306   │ │ :6379 │  │   │  │ :8081          │──┼─┼──→ 浏览器
│  │  └─────────┘ └───────┘  │   │  └───────┬────────┘  │ │
│  │  ┌─────────┐            │   │          │           │ │
│  │  │RabbitMQ │ :5672/:15672   │  miaosha-service     │ │
│  │  └─────────┘            │   │  miaosha-dao         │ │
│  └─────────────────────────┘   └──────────────────────┘ │
└─────────────────────────────────────────────────────────┘
```

应用通过 **localhost** 连中间件，docker-compose 把容器端口映射到宿主机——这是配置里写死的（`application.properties` 都是 `localhost`），所以不用改任何配置就能本地启动。
