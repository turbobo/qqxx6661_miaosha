#!/bin/bash
# 轮次之间重置库存和缓存（覆盖 stock 老系统 + ticket 新系统）
set -e

echo "[reset] 清空 MySQL（stock 老系统 + ticket 新系统）..."
docker exec miaosha-mysql mysql -uroot -proot -N -e "
USE m4a_miaosha;

-- stock 老系统：预置 sid=1~10 的库存（用于场景隔离，每个 sid 独立 10000 件）
INSERT IGNORE INTO stock (id, name, count, sale, version) VALUES
  (1, 'iphone',    10000, 0, 0),
  (2, 'mac',       10000, 0, 0),
  (3, 'ipad',      10000, 0, 0),
  (4, 'airpods',   10000, 0, 0),
  (5, 'watch',     10000, 0, 0),
  (6, 'homepod',   10000, 0, 0),
  (7, 'tv',        10000, 0, 0),
  (8, 'vision',    10000, 0, 0),
  (9, 'pencil',    10000, 0, 0),
  (10, 'airtag',   10000, 0, 0);
UPDATE stock SET count = 10000, sale = 0;
DELETE FROM stock_order;

-- ticket 新系统（CacheV5 / MQ / VerifiedUrl 用）
UPDATE ticket SET remaining_count = total_count, sold_count = 0;
DELETE FROM ticket_order;
DELETE FROM ticket_purchase_record;
DELETE FROM order_record;
" 2>&1 | grep -v 'Warning' || true

echo "[reset] 清空 Redis..."
docker exec miaosha-redis redis-cli FLUSHALL 2>&1 | tail -1

echo "[reset] 当前库存快照（按 sid 隔离查看）:"
docker exec miaosha-mysql mysql -uroot -proot -N -e "
USE m4a_miaosha;
SELECT CONCAT('  [stock ', id, ' ', name, ']  可用=', count-sale, '/', count, '  已售=', sale) FROM stock;
SELECT CONCAT('  [ticket] 总可用=', SUM(remaining_count), '/', SUM(total_count)) FROM ticket;
SELECT CONCAT('  [orders] stock_order=', (SELECT COUNT(*) FROM stock_order),
              '  ticket_order=', (SELECT COUNT(*) FROM ticket_order),
              '  order_record=', (SELECT COUNT(*) FROM order_record));
" 2>&1 | grep -v 'Warning'
