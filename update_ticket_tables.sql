-- 更新票券表日期字段类型
-- 将 date 字段从 DATE 类型改为 VARCHAR(10) 类型

-- 1. 更新 ticket 表
ALTER TABLE `ticket` MODIFY COLUMN `date` VARCHAR(10) NOT NULL COMMENT '票券日期';

-- 2. 更新 ticket_purchase_record 表
ALTER TABLE `ticket_purchase_record` MODIFY COLUMN `ticket_date` VARCHAR(10) NOT NULL COMMENT '票券日期';

-- 3. 更新现有数据（如果需要）
-- 将现有的日期数据转换为 yyyy-MM-dd 格式
UPDATE `ticket` SET `date` = DATE_FORMAT(`date`, '%Y-%m-%d') WHERE `date` IS NOT NULL;

-- 4. 验证更新结果
SELECT 'ticket表结构:' as info;
DESCRIBE `ticket`;

SELECT 'ticket_purchase_record表结构:' as info;
DESCRIBE `ticket_purchase_record`;

-- 5. 查看更新后的数据
SELECT 'ticket表数据:' as info;
SELECT `id`, `date`, `name`, `total_count`, `remaining_count`, `sold_count` FROM `ticket` LIMIT 5;

SELECT 'ticket_purchase_record表数据:' as info;
SELECT `id`, `ticket_date`, `user_id`, `ticket_code` FROM `ticket_purchase_record` LIMIT 5;
