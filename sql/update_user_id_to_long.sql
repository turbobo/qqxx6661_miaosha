-- 修改ticket_purchase_record表的user_id字段类型从VARCHAR改为BIGINT
-- 执行前请备份数据库

-- 1. 检查当前表结构
DESCRIBE ticket_purchase_record;

-- 2. 修改user_id字段类型
ALTER TABLE ticket_purchase_record MODIFY COLUMN user_id BIGINT NOT NULL COMMENT '用户ID';

-- 3. 验证修改结果
DESCRIBE ticket_purchase_record;

-- 4. 如果需要，可以添加索引来优化查询性能
-- CREATE INDEX idx_user_id ON ticket_purchase_record(user_id);
-- CREATE INDEX idx_user_date ON ticket_purchase_record(user_id, ticket_date);

-- 5. 检查是否有数据需要转换（如果有VARCHAR类型的用户ID数据）
-- SELECT DISTINCT user_id FROM ticket_purchase_record WHERE user_id REGEXP '^[0-9]+$';
-- 如果上面的查询返回空结果，说明所有user_id都是数字，可以直接转换
-- 如果有非数字的user_id，需要先清理数据

-- 注意事项：
-- 1. 执行前请备份数据库
-- 2. 确保所有相关的Java代码已经更新为Long类型
-- 3. 确保没有非数字的user_id数据
-- 4. 建议在测试环境先验证
