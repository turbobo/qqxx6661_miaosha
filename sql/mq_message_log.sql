CREATE TABLE IF NOT EXISTS mq_message_log (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    message_id VARCHAR(64) NOT NULL UNIQUE COMMENT '消息唯一ID',
    topic VARCHAR(128) NOT NULL COMMENT '队列/交换机',
    routing_key VARCHAR(128) DEFAULT '' COMMENT '路由键',
    message_body TEXT NOT NULL COMMENT '消息JSON内容',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0待发送 1已发送 2已确认 3已消费 -1发送失败',
    retry_count INT NOT NULL DEFAULT 0 COMMENT '重试次数',
    max_retry INT NOT NULL DEFAULT 5 COMMENT '最大重试次数',
    next_retry_time DATETIME COMMENT '下次重试时间',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_status_retry (status, next_retry_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MQ消息本地日志表';
