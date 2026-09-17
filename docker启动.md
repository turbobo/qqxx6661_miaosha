# 博物馆秒杀项目 · 本地开发指南

> 项目：`qqxx6661_miaosha`  
> 技术栈：Spring Boot 2.2.5 + Java 8 + MySQL 8 + Redis 6 + RabbitMQ 3  
> 运行方式：两种模式——① 全套 Docker（应用也在容器）② 中间件 Docker + 应用本地 IDEA 启动

> ⚠️ **端口已错开**：为避免与本机其他项目（如 buyer-show）的中间件冲突，
> 本项目宿主机端口统一调整为：
> - MySQL：`3307`（容器内 3306）
> - Redis：`6380`（容器内 6379）
> - RabbitMQ：`5673` / 管理台 `15673`（容器内 5672 / 15672）
> - 应用：`8081` 不变
>
> 容器之间通过服务名互通（`mysql:3306` 等），全套 Docker 模式下应用无需感知宿主机端口。

---

## 🐳 模式一 · 全套 Docker 启动（一键跑通，含应用）

```bash
cd /Users/qinghang/Documents/github/turbobo/qqxx6661_miaosha
docker compose up -d --build          # 首次构建需下载 Maven 依赖（约 5~15 分钟）
docker compose logs -f app            # 看到 Started MiaoshaWebApplication 即成功
```

启动后访问：
- 后端 API：`http://localhost:8081`
- Seckill 演进接口：`http://localhost:8081/seckill/stage0/wrong-order/1`（完整清单见下文）
- RabbitMQ 管理台：`http://localhost:15673` （guest / guest）

> 首次构建完成后，后续 `docker compose up -d` 秒起（镜像已缓存）。
> 修改代码后重新构建：`docker compose up -d --build app`

### SeckillController 全接口验证清单

```bash
# 阶段 0：无锁（反面教材）
curl http://localhost:8081/seckill/stage0/wrong-order/1
# 阶段 1：乐观锁
curl http://localhost:8081/seckill/stage1/optimistic-order/1
# 阶段 2：Guava 令牌桶 + 乐观锁 / 悲观锁
curl http://localhost:8081/seckill/stage2/optimistic-order-limited/1
curl http://localhost:8081/seckill/stage2/pessimistic-order/1
# 阶段 3：接口隐藏（仅 9:00-23:00 可用）+ 单用户限频
curl "http://localhost:8081/seckill/stage3/verify-hash?sid=1&userId=1"
curl "http://localhost:8081/seckill/stage3/verified-order?sid=1&userId=1&verifyHash=<上一步返回值>"
curl "http://localhost:8081/seckill/stage3/verified-order-limited?sid=1&userId=1&verifyHash=<hash>"
# 阶段 4：缓存对照 + 双写一致性 V1~V5
curl http://localhost:8081/seckill/stage4/stock-by-db/1
curl http://localhost:8081/seckill/stage4/stock-by-cache/1
curl http://localhost:8081/seckill/stage4/cache-v1/1
curl http://localhost:8081/seckill/stage4/cache-v2/1
curl http://localhost:8081/seckill/stage4/cache-v3/1
curl http://localhost:8081/seckill/stage4/cache-v4/1
curl "http://localhost:8081/seckill/stage4/cache-v5?sid=1&userId=1"
# 阶段 5：MQ 异步下单 + 轮询
curl "http://localhost:8081/seckill/stage5/order-with-mq?sid=1&userId=1"
curl "http://localhost:8081/seckill/stage5/purchase-result?sid=1&userId=1"
# 阶段 6：分布式锁 / 幂等 / 分布式令牌桶 / 熔断
curl http://localhost:8081/seckill/stage6/locked-order/1
curl "http://localhost:8081/seckill/stage6/idempotent-order?sid=1&userId=1&requestId=$(uuidgen)"
curl http://localhost:8081/seckill/stage6/token-bucket-order/1
curl http://localhost:8081/seckill/stage6/order-with-circuit-breaker/1
curl http://localhost:8081/seckill/stage6/circuit-breaker-status
```

> 前置数据：`sql/miaosha.sql` 已初始化 stock（sid=1）、user（id=1 张三）；
> 重置库存：`docker exec miaosha-mysql mysql -uroot -proot -e "use m4a_miaosha; update stock set sale=0, version=0 where id=1;"`

### 手动重建数据库（初始化脚本有变更时）

初始化脚本（`sql/*.sql` → `docker-entrypoint-initdb.d/01~04`）**仅在数据卷首次创建时执行**：

```bash
docker compose down -v      # ⚠️ 会删除全部数据卷（数据丢失），仅全新初始化时使用
docker compose up -d --build
```

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
- RabbitMQ 管理台：`http://localhost:15673` （guest / guest）
- 查数据：`docker exec -it miaosha-mysql mysql -uroot -proot m4a_miaosha`

> 本地 IDEA 连中间件时注意端口变化：MySQL `localhost:3307`、Redis `localhost:6380`、
> RabbitMQ `localhost:5673`。若沿用 `application.properties` 默认端口（3306/6379/5672），
> 需在 IDEA 运行配置中加环境变量覆盖，例如：
> `SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3307/m4a_miaosha?characterEncoding=utf-8&&serverTimezone=Asia/Shanghai`、
> `SPRING_REDIS_PORT=6380`、`SPRING_RABBITMQ_PORT=5673`。

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

`docker-compose.yml` 已将初始化脚本按顺序挂载到 `docker-entrypoint-initdb.d/`（**仅首次创建数据卷时自动执行**）：

| 顺序 | 脚本 | 作用 |
|---|---|---|
| 01 | init_ticket_database.sql | ticket / ticket_purchase_record / ticket_order / user |
| 02 | miaosha.sql | stock / stock_order / user / order_record（重建覆盖） |
| 03 | mq_message_log.sql | MQ 消息本地日志表 |
| 04 | add_idempotent_index.sql | ticket_order 幂等唯一索引 uk_user_date |

> ⚠️ **已有旧数据卷不会重跑初始化脚本**。缺表/缺索引时按需手动补（以缺 uk_user_date 索引和 mq_message_log 表为例）：

```bash
docker exec -i miaosha-mysql mysql -uroot -proot m4a_miaosha < sql/mq_message_log.sql
docker exec -i miaosha-mysql mysql -uroot -proot m4a_miaosha < sql/add_idempotent_index.sql
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
| 容器启动报 `port is already allocated` | 本机其他项目占用了同名端口 | 本项目已错开为 3307/6380/5673/15673；若仍冲突，改 `docker-compose.yml` 的宿主机端口 |
| IDEA 启动报 `Connection refused` 到 3306 / 6379 / 5672 | ①端口已错开为 3307/6380/5673 ②中间件没起来 | `docker compose ps` 看 status；IDEA 加环境变量 `SPRING_REDIS_PORT=6380` 等覆盖 |
| IDEA 启动报 `Table doesn't exist` | 数据库没初始化 | 补跑 Step 5 的 SQL，或 `docker compose down -v` 后重建 |
| 首次 `docker compose up -d --build` 很慢 | 容器内需下载全部 Maven 依赖 | 已配阿里云镜像，仅首次慢；后续构建命中层缓存秒级完成 |
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
| 数据库初始化 | `sql/miaosha.sql` + `init_ticket_database.sql` + `ticket_order_table.sql` |
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
│  │  │3307→3306│ │6380→6379│ │   │  │ :8081          │──┼─┼──→ 浏览器
│  │  └─────────┘ └───────┘  │   │  └───────┬────────┘  │ │
│  │  ┌─────────┐            │   │          │           │ │
│  │  │RabbitMQ │ 5673→5672  │   │  miaosha-service     │ │
│  │  │         │ 15673→15672│   │  miaosha-dao         │ │
│  │  └─────────┘            │   └──────────────────────┘ │
│  └─────────────────────────┘                            │
└─────────────────────────────────────────────────────────┘
```

端口映射说明（宿主机→容器）：MySQL `3307→3306`、Redis `6380→6379`、RabbitMQ `5673→5672`。
- **模式①（全套 Docker）**：`miaosha-app` 容器与中间件同一 Docker 网络，通过服务名直连（`mysql:3306`、`redis:6379`、`rabbitmq:5672`），不经过宿主机端口；
- **模式②（本地应用）**：IDEA 里的应用连宿主机端口 `localhost:3307 / 6380 / 5673`（需在运行配置里覆盖端口，见上文）。
