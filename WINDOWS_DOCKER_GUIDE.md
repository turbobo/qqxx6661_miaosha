# Windows 环境 Docker 开发指南

本文档介绍如何在 Windows 环境下搭建博物馆购票系统的开发环境：

- **Docker** 运行中间件（MySQL / Redis / RabbitMQ）
- **IDEA** 本地启动 Spring Boot 应用

```
┌──────────────────────────────────────────────┐
│               Windows 本机                    │
│                                               │
│  ┌─────────────┐     ┌─────────────────────┐ │
│  │   IDEA      │────▶│  Docker Desktop     │ │
│  │ Spring Boot │     │                     │ │
│  │   :8081     │     │  MySQL    :3306     │ │
│  └─────────────┘     │  Redis    :6379     │ │
│                      │  RabbitMQ :5672     │ │
│                      │  MQ管理台 :15672    │ │
│                      └─────────────────────┘ │
└──────────────────────────────────────────────┘
```

---

## 一、环境准备

### 1.1 安装 Docker Desktop

- 下载地址：https://www.docker.com/products/docker-desktop/
- 系统要求：Windows 10/11 64 位，需开启 WSL 2 或 Hyper-V
- 安装后打开 Docker Desktop，确认左下角状态为绿色 **Running**

### 1.2 验证安装

打开 PowerShell，执行：

```powershell
docker --version
docker compose version
```

### 1.3 资源分配

打开 Docker Desktop → Settings → Resources：

| 配置项 | 建议值 |
|--------|--------|
| Memory | 4 GB（至少 2 GB） |
| CPU | 2 核 |

### 1.4 项目文件

确保项目根目录包含以下文件：

```
qqxx6661_miaosha/
├── docker-compose.yml         # 服务编排配置
├── Dockerfile                 # 应用镜像构建（全栈模式用）
├── miaosha.sql                # 数据库初始化脚本
├── start-middleware.bat       # 一键启动中间件
├── stop.bat                   # 一键停止
├── logs.bat                   # 查看日志
├── miaosha-web/src/main/resources/
│   └── application.properties # 数据源 / RabbitMQ / 端口配置
└── miaosha-service/src/main/resources/
    └── application.properties # Redis 配置
```

---

## 二、启动中间件

### 2.1 启动

在项目根目录打开 PowerShell：

```powershell
docker compose up -d mysql redis rabbitmq
```

也可以双击 `start-middleware.bat` 一键启动。

首次执行会拉取镜像，耗时约 2-5 分钟。`miaosha.sql` 会自动导入到 MySQL。

### 2.2 确认就绪

```powershell
docker compose ps
```

预期输出：

```
NAME                STATUS
miaosha-mysql       Up (healthy)
miaosha-redis       Up (healthy)
miaosha-rabbitmq    Up (healthy)
```

状态显示 `health: starting` 说明还在启动中，等待即可（MySQL 约 30 秒）。

---

## 三、导入本地 MySQL 数据

Docker 中的 MySQL 与 Windows 本地的 MySQL 是两个完全独立的实例。Docker MySQL 首次启动时已通过 `miaosha.sql` 自动初始化了表结构和基础数据。

如果本地 MySQL 中有额外的业务数据需要迁移，按以下步骤操作。

### 3.1 处理端口冲突

本地 MySQL 和 Docker MySQL 同时运行时会争抢 3306 端口。两种方式任选：

**方式 A：先停掉本地 MySQL**

```powershell
net stop mysql
```

**方式 B：修改 Docker MySQL 端口**

编辑 `docker-compose.yml`，将 mysql 服务的端口映射改为 3307：

```yaml
ports:
  - "3307:3306"
```

然后重启 Docker MySQL：

```powershell
docker compose up -d mysql
```

### 3.2 导出数据

从本地 MySQL 导出：

```powershell
mysqldump -uroot -proot m4a_miaosha > dump.sql
```

如果本地 MySQL 在非默认端口，加 `-P` 参数：

```powershell
mysqldump -uroot -proot -P3307 m4a_miaosha > dump.sql
```

### 3.3 导入数据

PowerShell 不支持 `<` 重定向符，选择以下任一方式：

```powershell
# 方式 A：管道导入（适合小文件）
Get-Content dump.sql | docker compose exec -T mysql mysql -uroot -proot m4a_miaosha

# 方式 B：先拷贝进容器再执行（适合大文件，更稳定）
docker cp dump.sql miaosha-mysql:/tmp/dump.sql
docker compose exec mysql mysql -uroot -proot m4a_miaosha -e "source /tmp/dump.sql"
```

### 3.4 验证

```powershell
docker compose exec mysql mysql -uroot -proot -e "USE m4a_miaosha; SHOW TABLES;"
```

---

## 四、IDEA 启动应用

### 4.1 配置检查

`application.properties` 已指向 localhost，无需修改：

```properties
# miaosha-web/.../application.properties
spring.datasource.url=jdbc:mysql://localhost:3306/m4a_miaosha?...
spring.rabbitmq.host=localhost

# miaosha-service/.../application.properties
spring.redis.host=localhost
```

> 如果第三步将 Docker MySQL 改到了 3307 端口，这里需要同步修改 `spring.datasource.url` 中的端口号。

### 4.2 启动

在 IDEA 中运行 `miaosha-web` 模块的 `MiaoshaWebApplication` 主类。

启动成功后控制台会输出：

```
Started MiaoshaWebApplication in X.XXX seconds
Tomcat started on port(s): 8081
```

---

## 五、访问验证

| 服务 | 地址 | 说明 |
|------|------|------|
| 应用前端 | http://localhost:8081 | 博物馆购票页面 |
| RabbitMQ 管理台 | http://localhost:15672 | 账号 guest / guest |
| MySQL | localhost:3306 | 账号 root / root，库名 m4a_miaosha |
| Redis | localhost:6379 | 无密码 |

---

## 六、常用操作

### 查看日志

```powershell
docker compose logs -f mysql       # MySQL
docker compose logs -f rabbitmq    # RabbitMQ
docker compose logs --tail=50 redis  # 最近 50 行
```

或双击 `logs.bat` 选择服务。

### 进入容器

```powershell
docker compose exec mysql mysql -uroot -proot m4a_miaosha   # MySQL 命令行
docker compose exec redis redis-cli                           # Redis 命令行
docker compose exec rabbitmq rabbitmqctl list_queues          # MQ 队列状态
```

### 文件传输

**docker cp（一次性拷贝）：**

```powershell
# Windows → 容器
docker cp C:\backup\dump.sql miaosha-mysql:/tmp/dump.sql

# 容器 → Windows
docker cp miaosha-mysql:/tmp/dump.sql C:\backup\dump.sql
```

**Volume 挂载（目录级实时同步）：**

编辑 `docker-compose.yml`，在 mysql 服务的 volumes 中添加挂载：

```yaml
volumes:
  - ./miaosha.sql:/docker-entrypoint-initdb.d/miaosha.sql:ro      # 单个文件
  - ./sql-scripts:/docker-entrypoint-initdb.d                      # 整个目录
```

挂载目录下所有 `.sql` 文件会在 MySQL 首次启动时按文件名排序依次执行。

### 停止服务

```powershell
docker compose stop mysql redis rabbitmq   # 停止中间件（保留数据）
docker compose down                        # 停止并移除容器（保留数据）
docker compose down -v                     # 停止并清除所有数据
```

或双击 `stop.bat` 一键停止。

### 重新初始化数据库

清空 Docker MySQL 数据，重新导入 `miaosha.sql`：

```powershell
docker compose rm -f mysql
docker volume rm qqxx6661_miaosha_mysql-data   # 卷名 = 项目目录名_volume名
docker compose up -d mysql
```

> 不确定卷名可以用 `docker volume ls` 查看，找包含 `mysql-data` 的那个。

---

## 七、常见问题

### 端口被占用

```powershell
# 查看哪个进程占了端口
netstat -ano | findstr :3306
netstat -ano | findstr :8081

# 根据 PID 在任务管理器中结束，或修改 docker-compose.yml 中的端口映射
```

### 内存不足导致容器被 Kill

Docker Desktop → Settings → Resources → Memory 调到 4 GB 以上。

### MySQL 启动后连接被拒

MySQL 首次启动需要初始化数据目录，查看日志确认就绪：

```powershell
docker compose logs mysql
# 看到 "ready for connections" 再连接
```

### 拉取镜像很慢或失败

Docker Desktop → Settings → Docker Engine，添加镜像加速：

```json
{
  "registry-mirrors": [
    "https://docker.1ms.run",
    "https://docker.xuanyuan.me"
  ]
}
```

保存后 Docker Desktop 会自动重启。

### bat 脚本无法执行

PowerShell 执行策略限制，用管理员权限执行：

```powershell
Set-ExecutionPolicy -Scope CurrentUser RemoteSigned
```

或改用 CMD 运行。

---

## 附录：全栈模式

如果不想装 JDK / IDEA，也可以让所有服务都跑在 Docker 中：

```powershell
docker compose up -d --build
```

首次构建需编译 Maven 项目，约 5-10 分钟。完成后访问 http://localhost:8081 即可。

查看应用日志：

```powershell
docker compose logs -f app
```

---

**文档版本**：v2.0
**最后更新**：2026-07-16
