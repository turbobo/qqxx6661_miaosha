#!/bin/bash
# 演进接口吞吐量测试编排脚本
# 用法：./run_evolution.sh [full|minimal]
set -e
cd "$(dirname "$0")"

MODE=${1:-minimal}  # minimal 仅跑基线 3 个 @ 50VUs 15s，full 跑全矩阵

# 检查 JMeter
if ! command -v jmeter >/dev/null; then
  echo "❌ jmeter 未安装。请等 brew install 完成或手动安装"
  exit 1
fi

# 检查应用
if ! nc -z -G 2 localhost 8081; then
  echo "❌ 应用未在 8081 监听"
  exit 1
fi

mkdir -p result reports

# 场景定义：name|path|template|sid（sid 用于结果解析时按 sid 查订单数）
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

if [ "$MODE" = "full" ]; then
  SCENARIOS=("${SCENARIOS_FULL[@]}")
  VUS=(50 200 500 1000)
  DURATION=30
else
  SCENARIOS=("${SCENARIOS_MINIMAL[@]}")
  VUS=(50)
  DURATION=15
fi

echo "=== 测试模式: $MODE ==="
echo "=== 场景数: ${#SCENARIOS[@]}  并发档: ${VUS[*]}  持续: ${DURATION}s ==="
echo ""

# 清理旧结果
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
    ./reset_inventory.sh 2>&1 | tail -1
    jmeter -n -q user.properties -t "$template" \
           -Jthreads=$vu -Jrampup=$rampup -Jduration=$DURATION \
           -Jpath="$path" \
           -Jjtl="$(pwd)/result/${tag}.jtl" \
           > "result/${tag}.log" 2>&1 || echo "  ⚠️ jmeter 返回非零"
    # 记录本轮 stock 销量
    sold=$(docker exec miaosha-mysql mysql -uroot -proot -N -e "USE m4a_miaosha; SELECT sale FROM stock WHERE id=$sid;" 2>/dev/null | tr -d '\r')
    echo "  JMeter summary + stock[$sid].sale=$sold:"
    grep -E 'summary =' "result/${tag}.log" | tail -1 | sed 's/^/    /'
  done
done

echo ""
echo "=== 全部完成，生成报告 ==="
python3 parse_results.py > reports/REPORT.md
cat reports/REPORT.md
echo ""
echo "=== 报告已保存: jmeter/reports/REPORT.md ==="
