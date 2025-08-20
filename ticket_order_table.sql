-- 票券订单表
CREATE TABLE `ticket_order` (
  `id` int(11) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `order_no` varchar(64) NOT NULL COMMENT '订单编号',
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `ticket_id` int(11) NOT NULL COMMENT '票券ID',
  `ticket_code` varchar(128) NOT NULL COMMENT '票券编码',
  `ticket_date` varchar(20) NOT NULL COMMENT '购票日期',
  `status` int(11) NOT NULL DEFAULT '1' COMMENT '订单状态：1-待支付，2-已支付，3-已取消，4-已过期',
  `amount` bigint(20) NOT NULL COMMENT '订单金额（分）',
  `pay_time` datetime DEFAULT NULL COMMENT '支付时间',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  `update_time` datetime NOT NULL COMMENT '更新时间',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_ticket_id` (`ticket_id`),
  KEY `idx_ticket_code` (`ticket_code`),
  KEY `idx_status` (`status`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='票券订单表';

-- 插入示例数据
INSERT INTO `ticket_order` (`order_no`, `user_id`, `ticket_id`, `ticket_code`, `ticket_date`, `status`, `amount`, `create_time`, `update_time`) VALUES
('TO1705123456789001', 12345, 1, 'T2025011512345000001', '2025-01-15', 1, 10000, NOW(), NOW()),
('TO1705123456789002', 12346, 1, 'T2025011512346000002', '2025-01-15', 2, 10000, NOW(), NOW()),
('TO1705123456789003', 12347, 2, 'T2025011612347000001', '2025-01-16', 1, 10000, NOW(), NOW());
