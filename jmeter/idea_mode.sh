#!/bin/bash
# IDEA 模式压测编排：Docker 只启动中间件，应用由 IDEA 本地启动（宿主机 :8081）
# 用法：./idea_mode.sh [minimal|full|seckill]
#   minimal：OrderControllerV2 基线 3 方案 @ 50 VUs 15s（冒烟验证环境）
#   full   ：OrderControllerV2 全 10 方案 @ 50/200/500/1000 VUs 30s（约 40 分钟）
#   seckill：SeckillController 阶段 0~6（11 场景）@ 50/200/500 VUs 30s（约 30 分钟）
#
# 前置条件：
#   1. docker compose up -d mysql redis rabbitmq   # 只起中间件
#   2. IDEA 运行 MiaoshaWebApplication，加 --spring.profiles.active=local
#      （application-local.properties 已把连接指向宿主机 3307/6380/5673）
#   3. IDEA 运行配置 VM options 建议加 -Xms1g -Xmx1g -XX:+UseG1GC（与容器模式对齐，保证可比）
set -e
cd "$(dirname "$0")"

MODE=${1:-minimal}
# parse_results.py 固定读取该路径的轮次日志，复用同一路径
RUNLOG=/tmp/evolution_run.log
exec > >(tee "$RUNLOG") 2>&1

# ---------- 前置检查 ----------
if ! command -v jmeter >/dev/null; then
  echo "❌ jmeter 未安装。请先 brew install jmeter"
  exit 1
fi

echo "[precheck] 中间件健康状态："
MIDDLEWARE_OK=1
for svc in mysql redis rabbitmq; do
  health=$(docker compose ps --format '{{.Name}} {{.Health}}' "$svc" 2>/dev/null | awk '{print $2}')
  echo "  - $svc: ${health:-未运行}"
  if [ "$health" != "healthy" ]; then
    MIDDLEWARE_OK=0
  fi
done
if [ "$MIDDLEWARE_OK" != "1" ]; then
  echo "❌ 中间件未全部 healthy。请先执行：docker compose up -d mysql redis rabbitmq"
  exit 1
fi

if ! nc -z -G 2 localhost 8081; then
  echo "❌ 宿主机 8081 无监听。请在 IDEA 以 local profile 启动 MiaoshaWebApplication"
  exit 1
fi
if docker compose ps app 2>/dev/null | grep -q "Up"; then
  echo "⚠️ 检测到容器 app 正在运行，8081 可能来自容器而非 IDEA。建议 docker compose stop app 后重试"
fi

mkdir -p result reports

# ---------- 场景定义 ----------
# 格式：name|path|template|sid（sid 用于每轮查询 stock.sale 计算业务订单数）
# v2 场景与 run_evolution.sh 保持一致（sid 1~10）；seckill 场景使用 sid 11~21 隔离
declare -a SCENARIOS_MINIMAL=(
  "wrong|/v2/createWrongOrder/1|templates/simple_get.jmx|1"
  "optimistic|/v2/createOptimisticOrder/2|templates/simple_get.jmx|2"
  "pessimistic|/v2/createPessimisticOrder/3|templates/simple_get.jmx|3"
)

declare -a SCENARIOS_FULL=(
  "${SCENARIOS_MINIMAL[@]}"
  "cache_v1|/v2/createOrderWithCacheV1/4|templates/simple_get.jmx|4"
  "cache_v2|/v2/createOrderWithCacheV2/5|templates/simple_get.jmx|5"
  "cache_v3|/v2/createOrderWithCacheV3/6|templates/simple_get.jmx|6"
  "cache_v4|/v2/createOrderWithCacheV4/7|templates/simple_get.jmx|7"
  "cache_v5|/v2/createOrderWithCacheV5?sid=8&userId=1|templates/simple_get.jmx|8"
  "mq|/v2/createUserOrderWithMq?sid=9&userId=1|templates/simple_get.jmx|9"
  "hash_url|/v2/createOrderWithVerifiedUrl|templates/hash_protected.jmx|10"
)

# SeckillController 阶段 0~6：
#   单用户接口（内部固定用户，测纯锁开销）用 simple_get.jmx；
#   多用户接口 path 里带 ${__threadNum}（JMeter 运行时求值，bash 单引号保证不提前展开）；
#   验签两步 / 幂等 requestId 用专用 jmx。
declare -a SCENARIOS_SECKILL=(
  "s0_wrong|/seckill/stage0/wrong-order/11|templates/simple_get.jmx|11"
  "s1_optimistic|/seckill/stage1/optimistic-order/12|templates/simple_get.jmx|12"
  "s2_pessimistic|/seckill/stage2/pessimistic-order/13|templates/simple_get.jmx|13"
  "s2_limited|/seckill/stage2/optimistic-order-limited/14|templates/simple_get.jmx|14"
  "s3_hash|/seckill/stage3/verified-order|templates/seckill_stage3.jmx|15"
  "s4_cache_v5|/seckill/stage4/cache-v5|templates/seckill_stage4_cache_v5.jmx|16"
  "s5_mq|/seckill/stage5/order-with-mq|templates/seckill_stage5_mq.jmx|17"
  "s6_locked|/seckill/stage6/locked-order/18|templates/simple_get.jmx|18"
  's6_idempotent|/seckill/stage6/idempotent-order?sid=19&userId=${__threadNum}|templates/seckill_stage6_idempotent.jmx|19'
  "s6_token_bucket|/seckill/stage6/token-bucket-order/20|templates/simple_get.jmx|20"
  "s6_circuit|/seckill/stage6/order-with-circuit-breaker/21|templates/simple_get.jmx|21"
)

case "$MODE" in
  minimal)
    SCENARIOS=("${SCENARIOS_MINIMAL[@]}")
    VUS=(50)
    DURATION=15
    ;;
  full)
    SCENARIOS=("${SCENARIOS_FULL[@]}")
    VUS=(50 200 500 1000)
    DURATION=30
    ;;
  seckill)
    SCENARIOS=("${SCENARIOS_SECKILL[@]}")
    VUS=(50 200 500)
    DURATION=30
    ;;
  *)
    echo "❌ 未知模式：$MODE（可选 minimal|full|seckill）"
    exit 1
    ;;
esac

echo "=== IDEA 模式压测：$MODE ==="
echo "=== 场景数: ${#SCENARIOS[@]}  并发档: ${VUS[*]}  持续: ${DURATION}s ==="
echo "=== 前置：中间件在 Docker，应用在宿主机 IDEA（:8081）==="
echo ""

# ---------- 重置函数 ----------
# seckill 场景专用：预置 sid 11~21 各 10000 件（v2 场景沿用 reset_inventory.sh 的 sid 1~10）
reset_seckill() {
  echo "[reset] 清空 MySQL（seckill 场景 sid=11~21）..."
  docker exec miaosha-mysql mysql -uroot -proot -N -e "
USE m4a_miaosha;
INSERT IGNORE INTO stock (id, name, count, sale, version) VALUES
  (11,'seckill-s0',10000,0,0),(12,'seckill-s1',10000,0,0),
  (13,'seckill-s2p',10000,0,0),(14,'seckill-s2l',10000,0,0),
  (15,'seckill-s3',10000,0,0),(16,'seckill-s4',10000,0,0),
  (17,'seckill-s5',10000,0,0),(18,'seckill-s6l',10000,0,0),
  (19,'seckill-s6i',10000,0,0),(20,'seckill-s6t',10000,0,0),
  (21,'seckill-s6c',10000,0,0);
UPDATE stock SET count = 10000, sale = 0 WHERE id BETWEEN 11 AND 21;
DELETE FROM stock_order;
" 2>&1 | grep -v 'Warning' || true
  echo "[reset] 清空 Redis..."
  docker exec miaosha-redis redis-cli FLUSHALL 2>&1 | tail -1
}

# ---------- 执行 ----------
# 备份历史结果后清理（与 run_evolution.sh 共用 result/ 目录，避免误删历史 jtl）
if ls result/*.jtl >/dev/null 2>&1; then
  BACKUP_DIR="result/backup_$(date +%Y%m%d_%H%M%S)"
  mkdir -p "$BACKUP_DIR"
  cp result/*.jtl "$BACKUP_DIR/" 2>/dev/null || true
  echo "[backup] 历史 jtl 已备份至 $BACKUP_DIR"
fi
rm -f result/*.jtl result/*.log result/sid_map.csv
echo "scenario,vu,sid" > result/sid_map.csv

for scenario in "${SCENARIOS[@]}"; do
  IFS='|' read -r name path template sid <<< "$scenario"
  for vu in "${VUS[@]}"; do
    rampup=$((vu / 20))
    [ $rampup -lt 2 ] && rampup=2
    tag="${name}_${vu}"
    echo "$name,$vu,$sid" >> result/sid_map.csv
    echo "───────────────────────────────────────────"
    echo "[$tag] sid=$sid  path=$path  threads=$vu  rampup=$rampup  duration=$DURATION"
    if [ "$MODE" = "seckill" ]; then
      reset_seckill 2>&1 | tail -1
    else
      ./reset_inventory.sh 2>&1 | tail -1
    fi
    jmeter -n -q user.properties -t "$template" \
           -Jthreads=$vu -Jrampup=$rampup -Jduration=$DURATION \
           -Jpath="$path" -Jsid=$sid \
           -Jjtl="$(pwd)/result/${tag}.jtl" \
           > "result/${tag}.log" 2>&1 || echo "  ⚠️ jmeter 返回非零"
    # MQ 场景：压完等待消费者消化，记录队列积压（消费速率 vs 投递速率）
    if [[ "$name" == *mq* ]]; then
      sleep 15
      echo "  MQ 队列状态（name / messages）："
      docker exec miaosha-rabbitmq rabbitmqctl list_queues name messages 2>/dev/null \
        | grep -E '^(orderQueue|delCache)' | sed 's/^/    /'
    fi
    sold=$(docker exec miaosha-mysql mysql -uroot -proot -N -e "USE m4a_miaosha; SELECT sale FROM stock WHERE id=$sid;" 2>/dev/null | tr -d '\r')
    echo "  JMeter summary + stock[$sid].sale=$sold:"
    grep -E 'summary =' "result/${tag}.log" | tail -1 | sed 's/^/    /'
  done
done

echo ""
echo "=== 全部完成，生成报告 ==="
python3 parse_results.py > reports/REPORT_IDEA_MODE.md
# 标题替换为 IDEA 模式（parse_results.py 标题写死 OrderControllerV2）
python3 - <<'EOF'
import re
p = 'reports/REPORT_IDEA_MODE.md'
s = open(p, encoding='utf-8').read()
s = s.replace('# OrderControllerV2 演进接口吞吐量报告', '# IDEA 模式压测报告（Docker 中间件 + IDEA 本地应用）')
open(p, 'w', encoding='utf-8').write(s)
EOF
cat reports/REPORT_IDEA_MODE.md
echo ""
echo "=== 报告已保存: jmeter/reports/REPORT_IDEA_MODE.md ==="
