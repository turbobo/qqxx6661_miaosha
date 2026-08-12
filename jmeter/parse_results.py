#!/usr/bin/env python3
"""解析 result/*.jtl，生成 reports/REPORT.md"""
import csv, os, glob, sys
from collections import defaultdict
from datetime import datetime

def pct(sorted_list, p):
    if not sorted_list: return 0
    k = (len(sorted_list) - 1) * p / 100
    f, c = int(k), int(k) + 1
    if c >= len(sorted_list): return sorted_list[f]
    return sorted_list[f] + (sorted_list[c] - sorted_list[f]) * (k - f)

def parse_jtl(path, test_duration_s=15):
    times, successes = [], 0
    t_min, t_max = float('inf'), 0
    with open(path, 'r', encoding='utf-8', errors='ignore') as f:
        reader = csv.DictReader(f)
        for row in reader:
            try:
                elapsed = int(row.get('elapsed', 0))
                ts = int(row.get('timeStamp', 0))
                success = row.get('success', 'false').lower() == 'true'
            except ValueError:
                continue
            times.append(elapsed)
            if success: successes += 1
            t_min = min(t_min, ts)
            t_max = max(t_max, ts)
    if not times:
        return None
    times_sorted = sorted(times)
    # QPS 用测试窗口长度（而不是 last-first，避免长尾响应失真）
    effective_duration = max(test_duration_s, (t_max - t_min) / 1000)
    return {
        'count': len(times),
        'success': successes,
        'success_rate': successes / len(times) * 100,
        'avg': sum(times) / len(times),
        'min': min(times),
        'max': max(times),
        'p50': pct(times_sorted, 50),
        'p95': pct(times_sorted, 95),
        'p99': pct(times_sorted, 99),
        'qps': len(times) / effective_duration,
    }

def main():
    files = sorted(glob.glob('result/*.jtl'))
    if not files:
        print("result/ 下无 .jtl 文件"); sys.exit(0)

    # 从运行日志解析每轮的 stock.sale（实际下单数）
    sold_map = {}  # key = "scenario_vu" -> sold
    with open('/tmp/evolution_run.log', 'r', encoding='utf-8', errors='ignore') as f:
        current_tag = None
        for line in f:
            # 匹配形如 [wrong_50] sid=1  path=...
            m = line.strip()
            if m.startswith('[') and ']' in m and 'path=' in m:
                current_tag = m[1:m.index(']')]
            elif current_tag and 'stock[' in m and '.sale=' in m:
                try:
                    sold = int(m.split('.sale=')[1].split(':')[0])
                    sold_map[current_tag] = sold
                except:
                    pass

    rows = []
    for f in files:
        name = os.path.basename(f).replace('.jtl', '')
        parts = name.rsplit('_', 1)
        scenario = parts[0] if len(parts) == 2 else name
        vu = parts[1] if len(parts) == 2 else '?'
        m = parse_jtl(f)
        if m:
            m['business_orders'] = sold_map.get(name, '?')
            rows.append((scenario, vu, m))

    # Markdown 输出
    print(f"# OrderControllerV2 演进接口吞吐量报告\n")
    print(f"> 生成时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}  ")
    print(f"> 共 {len(rows)} 个场景\n")

    print("| 场景 | VUs | HTTP 请求 | HTTP 成功 | **业务订单** | HTTP 成功率 | 业务成功率 | QPS | Avg | P95 | P99 |")
    print("|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|")
    for scenario, vu, m in rows:
        bo = m['business_orders']
        biz_rate = '-'
        if isinstance(bo, int) and m['count'] > 0:
            biz_rate = f"{bo / m['count'] * 100:.1f}%"
        print(f"| {scenario} | {vu} | {m['count']} | {m['success']} | **{bo}** "
              f"| {m['success_rate']:.1f}% | {biz_rate} "
              f"| {m['qps']:.0f} | {m['avg']:.0f} | {m['p95']:.0f} | {m['p99']:.0f} |")

    # 演进对比
    print("\n## 演进 QPS 对比\n")
    vus = sorted({vu for _, vu, _ in rows}, key=lambda x: int(x) if x.isdigit() else 0)
    print("| 场景 | " + " | ".join(vus) + " |")
    print("|---" + "|---" * len(vus) + "|")
    scenarios = sorted({s for s, _, _ in rows})
    for s in scenarios:
        row_vals = []
        for vu in vus:
            m = next((m for sc, v, m in rows if sc == s and v == vu), None)
            row_vals.append(f"{m['qps']:.0f}" if m else "-")
        print(f"| {s} | " + " | ".join(row_vals) + " |")

    print("\n## 文件来源\n")
    for f in files:
        print(f"- `{f}`")

if __name__ == '__main__':
    main()
