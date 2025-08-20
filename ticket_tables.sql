-- 创建票券表
CREATE TABLE `ticket` (
  `id` int(11) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `date` date NOT NULL COMMENT '票券日期',
  `name` varchar(100) DEFAULT NULL COMMENT '票券名称',
  `total_count` int(11) NOT NULL DEFAULT 0 COMMENT '总票数',
  `remaining_count` int(11) NOT NULL DEFAULT 0 COMMENT '剩余票数',
  `sold_count` int(11) NOT NULL DEFAULT 0 COMMENT '已售票数',
  `version` int(11) NOT NULL DEFAULT 1 COMMENT '乐观锁版本号',
  `status` tinyint(4) NOT NULL DEFAULT 1 COMMENT '状态：1-正常，0-停售',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_date` (`date`) COMMENT '日期唯一索引',
  KEY `idx_status` (`status`) COMMENT '状态索引',
  KEY `idx_create_time` (`create_time`) COMMENT '创建时间索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='票券表';

-- 创建购买记录表
CREATE TABLE `ticket_purchase_record` (
  `id` int(11) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `order_id` varchar(64) NOT NULL COMMENT '订单ID',
  `user_id` varchar(64) NOT NULL COMMENT '用户ID',
  `ticket_id` int(11) NOT NULL COMMENT '票券ID',
  `ticket_date` date NOT NULL COMMENT '票券日期',
  `ticket_code` varchar(32) NOT NULL COMMENT '票券编号',
  `purchase_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '购买时间',
  `status` tinyint(4) NOT NULL DEFAULT 1 COMMENT '状态：1-有效，0-已使用，-1-已过期',
  `expire_time` timestamp NULL DEFAULT NULL COMMENT '过期时间',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_id` (`order_id`) COMMENT '订单ID唯一索引',
  UNIQUE KEY `uk_ticket_code` (`ticket_code`) COMMENT '票券编号唯一索引',
  KEY `idx_user_id` (`user_id`) COMMENT '用户ID索引',
  KEY `idx_ticket_date` (`ticket_date`) COMMENT '票券日期索引',
  KEY `idx_status` (`status`) COMMENT '状态索引',
  KEY `idx_purchase_time` (`purchase_time`) COMMENT '购买时间索引',
  KEY `idx_expire_time` (`expire_time`) COMMENT '过期时间索引',
  CONSTRAINT `fk_ticket_purchase_ticket` FOREIGN KEY (`ticket_id`) REFERENCES `ticket` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='票券购买记录表';

-- 插入初始数据
INSERT INTO `ticket` (`date`, `name`, `total_count`, `remaining_count`, `sold_count`, `version`, `status`) VALUES
(CURDATE(), '今日票券', 100, 100, 0, 1, 1),
(DATE_ADD(CURDATE(), INTERVAL 1 DAY), '明日票券', 150, 150, 0, 1, 1),
(DATE_ADD(CURDATE(), INTERVAL 2 DAY), '后日票券', 200, 200, 0, 1, 1); 