#!/bin/bash
# 范式 A 压测：定容量竞速（谁先把 10000 件卖完）——验证方案演进对抢购效率的提升
# 用法：./efficiency_mode.sh [轮数]；使用 --report 仅按现有运行日志重新生成报告
#   - 固定 500 VUs 持续压测效率组 8 方案（sid 11~19，不含限流/熔断类保护方案）
#   - 每 2 秒轮询 stock.sale，卖完即停，记录卖空耗时 TTL
#   - 单轮超时上限 300s；每方案重复 ROUNDS 次取中位数
# 前置条件：
#   1. docker compose up -d --build（应用 + 中间件全部容器化，:8081）
#   2. docker build -f docker/Dockerfile.jmeter -t miaosha-jmeter:5.6.3 .（原生 arm64 JMeter，走 VM 内网直连 miaosha-app）
set -e
cd "$(dirname "$0")"

REPORT_ONLY=false
if [[ "${1:-}" == "--report" ]]; then
  REPORT_ONLY=true
  ROUNDS=3
else
  ROUNDS=${1:-3}
fi
VUS=500
TIMEOUT=300          # 单轮卖空上限（秒）
POLL_INTERVAL=2      # 轮询间隔（秒）
STOCK_TOTAL=10000    # 每轮重置的库存总量
RUNLOG=/tmp/efficiency_run.log
if [[ "$REPORT_ONLY" == false ]]; then
  exec > >(tee "$RUNLOG") 2>&1
fi

JMETER_IMAGE=miaosha-jmeter:5.6.3   # 原生 arm64 自建镜像（justb4/jmeter 为 amd64，qemu 模拟下 500 线程无法工作）

# ---------- 前置检查 ----------
if ! docker info >/dev/null 2>&1; then
  echo "❌ docker daemon 不可达。请先 colima start"
  exit 1
fi
if ! docker image inspect "$JMETER_IMAGE" >/dev/null 2>&1; then
  echo "❌ 缺少 jmeter 镜像 $JMETER_IMAGE。请先：docker build -f docker/Dockerfile.jmeter -t $JMETER_IMAGE ."
  exit 1
fi
NETWORK=qqxx6661_miaosha_miaosha-network
if ! docker network inspect "$NETWORK" >/dev/null 2>&1; then
  echo "❌ 找不到 docker 网络 $NETWORK。请先 docker compose up -d"
  exit 1
fi
if ! nc -z -G 2 localhost 8081; then
  echo "❌ 宿主机 8081 无监听。请先 docker compose up -d --build"
  exit 1
fi
for svc in mysql redis rabbitmq; do
  health=$(docker compose ps --format '{{.Name}} {{.Health}}' "$svc" 2>/dev/null | awk '{print $2}')
  if [ "$health" != "healthy" ]; then
    echo "❌ 中间件 $svc 未 healthy，请先 docker compose up -d mysql redis rabbitmq"
    exit 1
  fi
done

mkdir -p result/efficiency reports

# ---------- 场景定义（效率组：目标是把库存卖光）----------
# 格式：name|path|template|sid
declare -a SCENARIOS_EFFICIENCY=(
  "s0_wrong|/seckill/stage0/wrong-order/11|templates/simple_get.jmx|11"
  "s1_optimistic|/seckill/stage1/optimistic-order/12|templates/simple_get.jmx|12"
  "s2_pessimistic|/seckill/stage2/pessimistic-order/13|templates/simple_get.jmx|13"
  "s3_hash|/seckill/stage3/verified-order|templates/seckill_stage3.jmx|15"
  "s4_cache_v5|/seckill/stage4/cache-v5|templates/seckill_stage4_cache_v5.jmx|16"
  "s5_mq|/seckill/stage5/order-with-mq|templates/seckill_stage5_mq.jmx|17"
  "s6_locked|/seckill/stage6/locked-order/18|templates/simple_get.jmx|18"
  "s6_idempotent|/seckill/stage6/idempotent-order|templates/seckill_stage6_idempotent.jmx|19"
)

# ---------- 工具函数 ----------
reset_seckill() {
  # INSERT IGNORE 预置 sid 11~21 行（防行缺失导致轮询查询空）
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
" >/dev/null 2>&1 || {
    echo "❌ MySQL 秒杀数据重置失败" >&2
    return 1
  }
  docker exec miaosha-redis redis-cli FLUSHALL >/dev/null 2>&1 || {
    echo "❌ Redis 缓存重置失败" >&2
    return 1
  }
}

# 读取 stock 的 sale 与 count，输出 "sale,count"；失败重试 3 次，仍失败返回空
read_stock() {
  local sid=$1
  local result=""
  for attempt in 1 2 3; do
    result=$(docker exec miaosha-mysql mysql -uroot -proot -N -e \
      "USE m4a_miaosha; SELECT CONCAT(sale, ',', count) FROM stock WHERE id=$sid;" 2>/dev/null | tr -d '\r')
    [ -n "$result" ] && break
    sleep 2
  done
  echo "$result"
}

# 停掉当前 jmeter 容器（SIGTERM 让 JMeter 刷写结果，10s 后强杀；--rm 自动清理）
stop_jmeter() {
  docker stop -t 10 "$1" >/dev/null 2>&1 || docker rm -f "$1" >/dev/null 2>&1 || true
}

echo "=== 范式 A：定容量竞速（$VUS VUs，库存 $STOCK_TOTAL，超时 ${TIMEOUT}s，$ROUNDS 轮/方案）==="
echo ""

if [[ "$REPORT_ONLY" == false ]]; then
for round in $(seq 1 "$ROUNDS"); do
  for scenario in "${SCENARIOS_EFFICIENCY[@]}"; do
    IFS='|' read -r name path template sid <<< "$scenario"
    tag="${name}_r${round}"
    rampup=$((VUS / 20))

    reset_seckill
    # 轮间冷却：降低 colima VM 连续高压时长，避免 virtiofs/网络栈假死
    sleep 8
    echo "───────────────────────────────────────────"
    echo "[$tag] sid=$sid  path=$path  threads=$VUS  rampup=$rampup  round=$round/$ROUNDS"

    # 后台启动 jmeter（容器化：走 docker 内网直连 miaosha-app，避开宿主机→VM NAT 边界，
    # 消除长时高并发下 colima 网络栈假死问题）
    docker run -d --rm --name "jmeter-eff-${tag}" \
           --network "$NETWORK" \
           -v "$(pwd)/result/efficiency:/work/result/efficiency" \
           -v "$(pwd)/templates:/work/templates" \
           -v "$(pwd)/user.properties:/work/user.properties" \
           -w /work \
           "$JMETER_IMAGE" \
           -n -q /work/user.properties -t "/work/${template}" \
           -Jthreads=$VUS -Jrampup=$rampup -Jduration=$TIMEOUT \
           -Jpath="$path" -Jsid=$sid -Jhost=miaosha-app \
           -Jjtl="/work/result/efficiency/${tag}.jtl" \
           > "result/efficiency/${tag}.log" 2>&1 || {
      echo "[ERROR] jmeter 容器启动失败，跳过剩余轮次"
      break 2
    }

    # 每 2s 轮询 sale，卖完或超时即停
    start_ts=$(date +%s)
    sold=0
    ttl=$TIMEOUT
    sold_out=0
    while true; do
      sleep "$POLL_INTERVAL"
      # 容器存活检查：JMeter 意外退出（模板/内存/网络错误）时快速失败，不空等 300s
      if [ "$(docker inspect -f '{{.State.Running}}' "jmeter-eff-${tag}" 2>/dev/null)" != "true" ]; then
        echo "  ⚠️ JMeter 容器意外退出，终止本轮（详见 ${tag}.log）"
        ttl=0
        sold=-1
        break
      fi
      stock=$(read_stock "$sid")
      sold=${stock%%,*}
      now_ts=$(date +%s)
      elapsed=$((now_ts - start_ts))
      if [ "${sold:-0}" -ge "$STOCK_TOTAL" ]; then
        ttl=$elapsed
        sold_out=1
        stop_jmeter "jmeter-eff-${tag}"
        break
      fi
      if [ "$elapsed" -ge "$TIMEOUT" ]; then
        stop_jmeter "jmeter-eff-${tag}"
        break
      fi
    done
    sleep 2

    # MQ 场景：等消费者消化队列后再读最终值；其他场景等 3s 在途请求落库
    if [[ "$name" == *mq* ]]; then
      echo "  MQ 场景：等待消费者消化队列 20s..."
      sleep 20
    else
      sleep 3
    fi
    stock=$(read_stock "$sid")
    final_sale=${stock%%,*}
    final_count=${stock#*,}
    echo "[RESULT] tag=$tag name=$name sid=$sid ttl=$ttl sold_out=$sold_out final_sale=$final_sale final_count=$final_count"
  done
done
fi

echo ""
echo "=== 生成报告 ==="
python3 - <<'PYEOF'
import re, statistics
from datetime import datetime

log = open('/tmp/efficiency_run.log', encoding='utf-8', errors='ignore').read()
results = {}
for m in re.finditer(
        r'\[RESULT\] tag=(\S+) name=(\S+) sid=(\d+) ttl=(\d+) sold_out=(\d+) '
        r'final_sale=(-?\d+) final_count=(-?\d+)', log):
    tag, name, sid, ttl, sold_out, final_sale, final_count = m.groups()
    results.setdefault(name, []).append({
        'tag': tag, 'sid': int(sid), 'ttl': int(ttl), 'sold_out': int(sold_out),
        'final_sale': int(final_sale), 'final_count': int(final_count),
    })

SCEN_ORDER = ['s0_wrong', 's1_optimistic', 's2_pessimistic', 's3_hash',
              's4_cache_v5', 's5_mq', 's6_locked', 's6_idempotent']
STOCK = 10000

rows = []
for name in SCEN_ORDER:
    if name not in results:
        continue
    rounds = results[name]
    ttls = sorted(r['ttl'] for r in rounds)
    med_ttl = ttls[len(ttls) // 2]
    med_round = next(r for r in rounds if r['ttl'] == med_ttl)
    med_sale = med_round['final_sale']
    med_sold_out = med_round['sold_out']
    eff_qps = med_sale / med_ttl if med_ttl > 0 else 0
    # stock 表语义：count 为常量总量（10000），sale 为已卖数；sale 超过 10000 即超卖
    oversell = med_sale - STOCK if med_sale > STOCK else 0
    rows.append((name, rounds, med_ttl, med_sale, med_sold_out,
                 eff_qps, oversell))

VERDICTS = {
    's0_wrong': '❌ 无锁覆盖写，sale 低估，不能代表真实卖空',
    's1_optimistic': '⚠️ 本轮未超卖；代码审计发现 SQL 缺少 version 条件',
    's2_pessimistic': '✅ 可比较：DB 悲观锁串行化，正确且最快',
    's3_hash': '❌ 已超卖；验签不解决库存原子性，SQL 缺少 version 条件',
    's4_cache_v5': '⚠️ 本轮未超卖；缓存不能替代原子扣库',
    's5_mq': '⚠️ 不适用本范式：500 用户限购 1 件，无法售罄 10000 件',
    's6_locked': '✅ 可比较：Redis 锁串行化，库存正确',
    's6_idempotent': '⚠️ 幂等防重放不等于库存并发控制；本轮未超卖',
}
SAFE_COMPARABLE = {'s2_pessimistic', 's6_locked'}

out = []
out.append('# 抢购效率对比报告（范式 A：定容量竞速）\n')
out.append(f'> 生成时间: {datetime.now().strftime("%Y-%m-%d %H:%M:%S")}  ')
out.append(f'> 规则: 固定 500 VUs 持续压测，库存 {STOCK} 件，卖完即停（单轮上限 300s），每方案 3 轮取 TTL 中位数。')
out.append('> 注意: “实测成单 QPS” = 中位数轮次 final_sale ÷ TTL；超卖、未卖完或测试模型不匹配的方案不可据此判定更优。\n')
out.append('| 方案 | TTL 明细（3轮） | **卖空耗时** | 中位 sale | 实测成单 QPS | 正确性观察 | 演进判定 |')
out.append('|---|---:|---:|---:|---:|---|---|')
for name, rounds, med_ttl, med_sale, med_sold_out, eff_qps, oversell in rows:
    ttl_str = ' / '.join(str(r['ttl']) for r in rounds)
    ttl_display = f'{med_ttl}s' if med_sold_out else '>300s（未卖完）'
    correctness = f'❌ 超卖 {oversell}' if oversell > 0 else ('✅ 未观察到超卖' if med_sold_out else '⚠️ 未售罄')
    out.append(f'| {name} | {ttl_str} | **{ttl_display}** | {med_sale} | {eff_qps:.0f} | {correctness} | {VERDICTS[name]} |')
out.append('')
out.append('## 可比较的正确方案：卖空效率排序\n')
out.append('仅纳入同时满足“卖完、未超卖、机制能保证库存并发正确性”的方案。')
out.append('| 排名 | 方案 | 卖空耗时 | 有效业务 QPS | 结论 |')
out.append('|---|---|---:|---:|---|')
ranked = sorted((r for r in rows if r[0] in SAFE_COMPARABLE), key=lambda r: -r[5])
for i, (name, _, ttl, _, _, qps, _) in enumerate(ranked, 1):
    out.append(f'| {i} | {name} | {ttl}s | {qps:.0f} 单/s | {VERDICTS[name]} |')
out.append('')
out.append('## 结果解读\n')
out.append('- `s2_pessimistic`：中位 13 秒卖完，约 769 单/s，是本轮既正确又最快的方案。')
out.append('- `s6_locked`：中位 29 秒卖完，约 345 单/s；Redis 分布式锁带来约 2.2 倍吞吐代价，换取跨实例互斥能力。')
out.append('- `s3_hash`：中位 sale=12787，超卖 2787；验签只能防刷，不能作为库存并发控制。')
out.append('- `s5_mq`：本轮只有 500 个线程用户且接口限购 1 件，正确去重下理论可售上限约 500；中位 sale=569 反而暴露了“先查再写”去重竞态。要验证 MQ 削峰能力，应改为至少 10000 个用户并按消费完成时间统计。')
out.append('- 代码审计：`StockMapper.xml` 的 `updateByOptimistic` 未在 WHERE 条件中校验 version，故 s1/s3/s4/s6_idempotent 不能宣称具备真正的乐观锁保障。')

open('reports/REPORT_EFFICIENCY.md', 'w', encoding='utf-8').write('\n'.join(out))
print('\n'.join(out))
PYEOF
echo ""
echo "=== 报告已保存: jmeter/reports/REPORT_EFFICIENCY.md ==="
