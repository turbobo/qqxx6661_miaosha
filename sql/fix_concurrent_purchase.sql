-- 修复并发抢购问题：添加唯一约束防止同一用户购买多张同日期票券

-- 方案1：添加唯一约束（推荐）
-- 在ticket_order表上添加(user_id, ticket_date)的唯一约束
ALTER TABLE `ticket_order` 
ADD UNIQUE KEY `uk_user_ticket_date` (`user_id`, `ticket_date`);

-- 如果表中已有重复数据，需要先清理
-- 查询重复数据
-- SELECT user_id, ticket_date, COUNT(*) as count 
-- FROM ticket_order 
-- GROUP BY user_id, ticket_date 
-- HAVING count > 1;

-- 删除重复数据（保留最早的记录）
-- DELETE t1 FROM ticket_order t1
-- INNER JOIN ticket_order t2 
-- WHERE t1.id > t2.id 
-- AND t1.user_id = t2.user_id 
-- AND t1.ticket_date = t2.ticket_date;
