package cn.monitor4all.miaoshaservice.config;

import cn.monitor4all.miaoshaservice.service.ReliableMessageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

/**
 * RabbitMQ确认机制配置类
 * 配置Publisher Confirm和Return回调，与本地消息表配合实现可靠消息投递
 */
@Configuration
public class RabbitMqConfirmConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(RabbitMqConfirmConfig.class);

    /**
     * 使用@Lazy避免与ReliableMessageService的循环依赖
     * （ReliableMessageService依赖RabbitTemplate，而RabbitTemplate的回调依赖ReliableMessageService）
     */
    @Autowired
    @Lazy
    private ReliableMessageService reliableMessageService;

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);

        // 设置发布确认回调，通过ReliableMessageService更新本地消息表状态
        rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
            String messageId = correlationData != null ? correlationData.getId() : null;
            if (messageId == null) {
                LOGGER.warn("confirmCallback: correlationData或messageId为null, ack={}", ack);
                return;
            }
            if (ack) {
                LOGGER.info("Publisher Confirm: 消息已确认, messageId={}", messageId);
            } else {
                LOGGER.error("Publisher Confirm: 消息nack, messageId={}, cause={}", messageId, cause);
            }
            reliableMessageService.confirmCallback(messageId, ack);
        });

        // 设置返回回调（消息无法路由到队列时触发）
        rabbitTemplate.setReturnCallback((message, replyCode, replyText, exchange, routingKey) -> {
            LOGGER.error("消息被退回，exchange[{}], routingKey[{}], replyCode[{}], replyText[{}], message[{}]",
                exchange, routingKey, replyCode, replyText, message);
        });

        return rabbitTemplate;
    }
}
