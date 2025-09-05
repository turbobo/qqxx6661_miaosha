-- m4a_miaosha.stock definition

CREATE TABLE `stock` (
                         `id` int(11) unsigned NOT NULL AUTO_INCREMENT,
                         `name` varchar(50) NOT NULL DEFAULT '' COMMENT '名称',
                         `count` int(11) NOT NULL COMMENT '库存',
                         `sale` int(11) NOT NULL COMMENT '已售',
                         `version` int(11) NOT NULL COMMENT '乐观锁，版本号',
                         PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8;


-- m4a_miaosha.stock_order definition

CREATE TABLE `stock_order` (
                               `id` int(11) unsigned NOT NULL AUTO_INCREMENT,
                               `sid` int(11) NOT NULL COMMENT '库存ID',
                               `name` varchar(30) NOT NULL DEFAULT '' COMMENT '商品名称',
                               `user_id` int(11) NOT NULL DEFAULT '0',
                               `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '创建时间',
                               PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=70781 DEFAULT CHARSET=utf8;


-- m4a_miaosha.ticket definition

CREATE TABLE `ticket` (
                          `id` int(11) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                          `date` varchar(20) NOT NULL COMMENT '票券日期',
                          `name` varchar(100) DEFAULT NULL COMMENT '票券名称',
                          `total_count` int(11) NOT NULL DEFAULT '0' COMMENT '总票数',
                          `remaining_count` int(11) NOT NULL DEFAULT '0' COMMENT '剩余票数',
                          `sold_count` int(11) NOT NULL DEFAULT '0' COMMENT '已售票数',
                          `version` int(11) NOT NULL DEFAULT '1' COMMENT '乐观锁版本号',
                          `status` tinyint(4) NOT NULL DEFAULT '1' COMMENT '状态：1-正常，0-停售',
                          `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                          `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                          PRIMARY KEY (`id`),
                          UNIQUE KEY `uk_date` (`date`) COMMENT '日期唯一索引',
                          KEY `idx_status` (`status`) COMMENT '状态索引',
                          KEY `idx_create_time` (`create_time`) COMMENT '创建时间索引'
) ENGINE=InnoDB AUTO_INCREMENT=19 DEFAULT CHARSET=utf8mb4 COMMENT='票券表';


-- m4a_miaosha.ticket_order definition

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
) ENGINE=InnoDB AUTO_INCREMENT=15635 DEFAULT CHARSET=utf8mb4 COMMENT='票券订单表';


-- m4a_miaosha.ticket_purchase_record definition

CREATE TABLE `ticket_purchase_record` (
                                          `id` int(11) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                                          `order_id` varchar(64) NOT NULL COMMENT '订单ID',
                                          `user_id` bigint(20) NOT NULL COMMENT '用户ID',
                                          `ticket_id` int(11) NOT NULL COMMENT '票券ID',
                                          `ticket_date` varchar(20) NOT NULL COMMENT '票券日期',
                                          `ticket_code` varchar(32) NOT NULL COMMENT '票券编号',
                                          `purchase_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '购买时间',
                                          `status` tinyint(4) NOT NULL DEFAULT '1' COMMENT '状态：1-有效，0-已使用，-1-已过期',
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
                                          KEY `fk_ticket_purchase_ticket` (`ticket_id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COMMENT='票券购买记录表';


-- m4a_miaosha.`user` definition

CREATE TABLE `user` (
                        `id` bigint(20) NOT NULL AUTO_INCREMENT,
                        `user_name` varchar(255) NOT NULL DEFAULT '',
                        PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1062576 DEFAULT CHARSET=utf8mb4;