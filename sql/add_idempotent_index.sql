-- 幂等性兜底：防止同一用户同一日期重复购票
-- 注意：如果已执行过 fix_concurrent_purchase.sql 中的 uk_user_ticket_date 约束，
-- 则本文件中的索引已存在，无需重复执行。可通过以下语句确认：
--   SHOW INDEX FROM ticket_order WHERE Key_name = 'uk_user_date';

-- 添加唯一索引（幂等性数据库层兜底）
ALTER TABLE `ticket_order`
ADD UNIQUE INDEX `uk_user_date` (`user_id`, `ticket_date`);

-- 验证索引是否创建成功
-- SHOW INDEX FROM ticket_order;
