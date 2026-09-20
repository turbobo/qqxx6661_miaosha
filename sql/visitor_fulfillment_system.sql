-- 购票成功独立成立，访客预约通过自动补偿与人工兜底完成。
-- 已有数据库可手动执行本脚本；Docker 新数据卷会按 05 前缀自动初始化。

INSERT INTO stock (id, name, count, sale, version)
VALUES (22, '文明的刻度特展联票', 10000, 0, 0)
ON DUPLICATE KEY UPDATE name = VALUES(name);

CREATE TABLE IF NOT EXISTS fulfillment_ticket_order (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    order_no VARCHAR(64) NOT NULL COMMENT '购票订单号',
    user_id BIGINT NOT NULL COMMENT '预约人编号',
    stock_id INT NOT NULL COMMENT '库存ID',
    visit_slot VARCHAR(32) NOT NULL COMMENT '参观场次',
    ticket_status VARCHAR(24) NOT NULL COMMENT 'TICKET_SUCCESS',
    fulfillment_status VARCHAR(24) NOT NULL COMMENT 'VISITOR_PENDING/VISITOR_SUCCESS/MANUAL_PENDING',
    create_time DATETIME NOT NULL COMMENT '创建时间',
    update_time DATETIME NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_no (order_no),
    UNIQUE KEY uk_user_slot (user_id, stock_id, visit_slot),
    KEY idx_fulfillment_status (fulfillment_status, update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='购票权益订单：购票成功后不因访客预约失败而取消';

CREATE TABLE IF NOT EXISTS visitor_reservation_task (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    order_no VARCHAR(64) NOT NULL COMMENT '购票订单号',
    reservation_no VARCHAR(64) NOT NULL COMMENT '访客预约幂等号',
    user_id BIGINT NOT NULL COMMENT '预约人编号',
    visit_slot VARCHAR(32) NOT NULL COMMENT '参观场次',
    status VARCHAR(24) NOT NULL COMMENT 'PENDING/PROCESSING/RETRYING/VISITOR_SUCCESS/MANUAL_PENDING',
    retry_count INT NOT NULL DEFAULT 0 COMMENT '已自动重试次数',
    next_retry_time DATETIME DEFAULT NULL COMMENT '下次自动重试时间',
    last_error_code VARCHAR(32) DEFAULT NULL COMMENT '最近失败码',
    last_error_message VARCHAR(255) DEFAULT NULL COMMENT '最近失败原因',
    operator VARCHAR(64) DEFAULT NULL COMMENT '人工处理人',
    complete_time DATETIME DEFAULT NULL COMMENT '访客预约完成时间',
    create_time DATETIME NOT NULL COMMENT '创建时间',
    update_time DATETIME NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_task_order (order_no),
    UNIQUE KEY uk_reservation_no (reservation_no),
    KEY idx_task_retry (status, next_retry_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='访客预约履约任务：失败只补偿、不影响购票权益';

CREATE TABLE IF NOT EXISTS fulfillment_outbox_event (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    event_id VARCHAR(64) NOT NULL COMMENT '事件幂等ID',
    order_no VARCHAR(64) NOT NULL COMMENT '购票订单号',
    event_type VARCHAR(32) NOT NULL COMMENT 'VISITOR_RESERVATION_REQUESTED',
    payload TEXT NOT NULL COMMENT '事件内容',
    status VARCHAR(24) NOT NULL COMMENT 'PENDING/SENT/CONSUMED/MANUAL',
    retry_count INT NOT NULL DEFAULT 0 COMMENT '投递次数',
    next_retry_time DATETIME DEFAULT NULL COMMENT '下次投递时间',
    create_time DATETIME NOT NULL COMMENT '创建时间',
    update_time DATETIME NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_event_id (event_id),
    KEY idx_outbox_dispatch (status, next_retry_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='访客预约本地事件表：与购票订单同事务写入';
