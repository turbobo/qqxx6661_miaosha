# 根目录整理方案

> 当前状态：根目录 52 项，含 30 份演进文档、7 份 SQL、3 份 .bat 脚本，以及 2 个位置错误的散落文件  
> 目标：根目录只保留项目骨架，其余按类型归位  
> 风险：所有文件只做 `git mv`，可被 git 完整追溯回滚；建议整理前 commit 一次当前状态作为锚点

---

## 📊 现状盘点

| 类别 | 数量 | 占根目录比重 |
|---|---|---|
| 演进文档 MD | 30 | 58% |
| 业务目录 | 5 | 10% |
| SQL 脚本 | 7 | 13% |
| 隐藏元数据 | 3 | 6% |
| 项目骨架文件 | 6 | 12% |
| 其他（bat/logs/错位文件） | 4 | 8% |

**根因**：每完成一个演进功能就写一份 `XXX_README.md` 堆在根目录，日积月累 30 份文档把骨架淹没了。

---

## 🎯 整理目标

根目录整理后只剩 **12 项**（vs 当前 52）：

```
qqxx6661_miaosha/
├── README.md                 # 项目总说明
├── SETUP.md                  # 本地开发指南
├── LICENSE                   # MIT 许可证
├── REORG_PLAN.md             # 本方案（可删）
├── pom.xml                   # Maven 父 pom
├── docker-compose.yml        # 容器编排
├── Dockerfile                # 应用镜像
├── .dockerignore
├── .gitignore                # (建议补全)
│
├── miaosha-dao/              # 业务模块
├── miaosha-service/
├── miaosha-web/
├── miaosha-job/
├── src/
│
├── docs/                     # 🆕 30 份演进文档按主题归位
│   ├── cache/                #   8 篇缓存演进
│   ├── concurrency/          #   4 篇并发与锁
│   ├── async/                #   2 篇异步/MQ
│   ├── ticket-code/          #   3 篇票号
│   ├── api-flow/             #   5 篇接口流程
│   ├── refactor/             #   4 篇工程重构
│   └── guides/               #   4 篇指南/总结
│
├── sql/                      # 🆕 7 个 SQL 脚本
└── scripts/win/              # 🆕 3 个 Windows 脚本
```

**附带清理**：
- `TicketCacheManagerTest.java` 移入 `miaosha-service/src/test/java/.../cache/`
- `application-rabbitmq.properties` 合并入 `miaosha-service/src/main/resources/`
- `logs/` 加入 `.gitignore` 并从 git 移除
- `jmeter/` 保留（本来就是独立目录）

---

## 📋 详细移动清单

### 1. 新建目录

```bash
mkdir -p docs/cache docs/concurrency docs/async docs/ticket-code \
         docs/api-flow docs/refactor docs/guides
mkdir -p sql
mkdir -p scripts/win
```

### 2. 文档归档（30 份 → `docs/`）

#### `docs/cache/`（缓存体系，8 份）
```
REDIS_CACHE_README.md
CACHE_KEY_STRUCTURE_README.md
CACHE_FALLBACK_README.md
CACHE_DELETE_MESSAGE_REFACTOR_README.md
ASYNC_CACHE_DELETE_README.md
DELAYED_CACHE_DELETE_README.md
TICKET_CACHE_SERVICE_README.md
TICKET_VALIDATION_README.md
```

#### `docs/concurrency/`（并发与锁，4 份）
```
PESSIMISTIC_LOCK_PURCHASE_README.md
PESSIMISTIC_LOCK_API_README.md
PESSIMISTIC_LOCK_V2_README.md
DISTRIBUTED_RATE_LIMIT_README.md
```

#### `docs/async/`（异步与 MQ，2 份）
```
ASYNC_PURCHASE_README.md
MIAOSHA_INVENTORY_UPDATE_README.md
```

#### `docs/ticket-code/`（票号生成，3 份）
```
TICKET_CODE_GENERATION_README.md
TICKET_CODE_UNIQUENESS_README.md
SEQUENCE_GENERATOR_IMPROVEMENT_README.md
```

#### `docs/api-flow/`（接口与流程，5 份）
```
GET_VERIFY_HASH_API_README.md
PURCHASE_FLOW_VERIFY_HASH_README.md
USER_PURCHASE_STATUS_README.md
TICKET_ORDER_REFACTOR_README.md
USER_ID_TYPE_CHANGE_README.md
```

#### `docs/refactor/`（工程重构，4 份）
```
CIRCULAR_DEPENDENCY_REFACTOR_README.md
DEPENDENCY_INJECTION_REFACTOR_README.md
DAILY_TICKET_UPDATE_TASK_README.md
TROUBLESHOOTING_GUIDE.md
```

#### `docs/guides/`（指南与总结，4 份）
```
MUSEUM_TICKET_GRAB_EVOLUTION_RESUME.md
WINDOWS_DOCKER_GUIDE.md
README.md              ← 注意：如果原 README 是项目总说明，保留在根；这里放的是子项目文档（如另有）
SETUP.md               ← 同上，根目录那份保留
```

> ⚠️ `README.md` 和 `SETUP.md` 在根目录**保留**，不复制进 `docs/guides/`。实际归档到 `docs/guides/` 的是另外 2 份（见清单）。

### 3. SQL 归档（7 份 → `sql/`）

```
miaosha.sql
init_ticket_database.sql
ticket_tables.sql
ticket_order_table.sql
update_ticket_tables.sql
update_user_id_to_long.sql
fix_concurrent_purchase.sql
```

⚠️ **注意**：`docker-compose.yml` 中挂载路径写的是 `./miaosha.sql`，移动后必须同步修改：
```yaml
volumes:
  - ./sql/miaosha.sql:/docker-entrypoint-initdb.d/miaosha.sql:ro
```
`SETUP.md` 中的 `docker exec ... < init_ticket_database.sql` 命令也要改成 `< sql/init_ticket_database.sql`。

### 4. Windows 脚本归档（3 份 → `scripts/win/`）

```
start-middleware.bat
stop.bat
logs.bat
```

`WINDOWS_DOCKER_GUIDE.md` 中如果有引用这些脚本的路径，也要同步更新。

### 5. 散落文件归位

| 文件 | 当前位置 | 目标位置 |
|---|---|---|
| `TicketCacheManagerTest.java` | 根目录 | `miaosha-service/src/test/java/cn/monitor4all/miaoshaservice/cache/TicketCacheManagerTest.java` |
| `application-rabbitmq.properties` | 根目录 | 合并到 `miaosha-service/src/main/resources/application.properties` 或独立为 `application-mq.properties` |

### 6. .gitignore 补全

建议在 `.gitignore` 增加：
```
# 运行产物
logs/
*.log

# IDEA
.idea/
*.iml
out/

# macOS
.DS_Store

# Maven
target/
```

如果 `logs/` 已被 git 追踪，需要先：
```bash
git rm -r --cached logs/
git commit -m "chore: remove logs from git tracking"
```

---

## 🛡️ 风险控制

| 风险 | 应对 |
|---|---|
| `docker-compose.yml` 路径写死导致启动失败 | 整理后跑一次 `docker compose config` 校验，再 `docker compose up -d mysql` 实测 |
| SQL 路径在 `SETUP.md` / `WINDOWS_DOCKER_GUIDE.md` 中散落 | grep 全文搜索 `\.sql` 找所有引用，统一改 |
| Windows .bat 路径被旧文档引用 | grep `\.bat` 找引用 |
| `TicketCacheManagerTest.java` 移过去后包名不匹配 | 检查文件头 `package xxx;`，确保与目标目录一致 |
| 整理到一半想放弃 | 每做完一组就 commit，可随时回退到任一阶段 |

---

## 📝 执行步骤（推荐分 6 次 commit）

```bash
# ① 先打锚点
git add -A
git commit -m "chore: 整理前的状态快照（reorg baseline）"

# ② 建目录
mkdir -p docs/{cache,concurrency,async,ticket-code,api-flow,refactor,guides} sql scripts/win
git add .
git commit -m "chore: 建立 docs/sql/scripts 目录骨架"

# ③ 文档归档（30 份，最大动作）
git mv REDIS_CACHE_README.md docs/cache/
git mv CACHE_KEY_STRUCTURE_README.md docs/cache/
# ...（其余 28 份同理）
git commit -m "docs: 归档 30 份演进文档到 docs/ 按主题分组"

# ④ SQL 归档（同步修 docker-compose.yml 和 SETUP.md 中的路径）
git mv *.sql sql/
# 编辑 docker-compose.yml 改 ./miaosha.sql → ./sql/miaosha.sql
# 编辑 SETUP.md 改对应命令
git add docker-compose.yml SETUP.md
git commit -m "chore: sql 脚本归位 + 同步更新引用路径"

# ⑤ 脚本 + 散落文件归位
git mv *.bat scripts/win/
git mv TicketCacheManagerTest.java miaosha-service/src/test/java/cn/monitor4all/miaoshaservice/cache/
git mv application-rabbitmq.properties miaosha-service/src/main/resources/
# 修 TicketCacheManagerTest.java 的 package 声明
git add .
git commit -m "chore: bat 脚本 + 散落文件归位"

# ⑥ .gitignore 补全 + 清理 logs/
echo "logs/" >> .gitignore
git rm -r --cached logs/ 2>/dev/null
git add .gitignore
git commit -m "chore: 补全 .gitignore 并从追踪中移除 logs"
```

---

## ✅ 验证清单

整理完成后，逐项确认：

- [ ] `docker compose config` 无报错
- [ ] `docker compose up -d mysql` 能正常启动（验证 sql 路径）
- [ ] `mvn clean install -DskipTests` 能构建成功（验证 pom 和模块引用未破）
- [ ] IDEA 重新打开项目，不报找不到文件
- [ ] `find . -maxdepth 1 -type f | wc -l` 数一下根目录文件数，预期 ≤ 8
- [ ] `git log --oneline` 看到 6 次整理 commit
- [ ] 前端页面 `http://localhost:8081/` 能正常打开
- [ ] DBeaver 仍能连上 MySQL

---

## 🔙 回滚

如果整理过程中出错，按 commit 回退即可：

```bash
git log --oneline              # 看 6 个整理 commit 的 hash
git reset --soft <baseline-hash>  # 回到整理前的锚点（文件保留在暂存区）
git checkout .                 # 丢弃所有改动
```

---

## 📌 备注

- 本方案本身（`REORG_PLAN.md`）整理完成后可以删除，或留作项目演进记录。
- 不涉及的目录：`jmeter/`（本来就在自己目录里）、`miaosha-job/`（是否纳入父 pom 是另一回事，本方案不动它）。
- 不动业务代码、不改业务逻辑、不碰数据库 schema，只是**物理归档**。
