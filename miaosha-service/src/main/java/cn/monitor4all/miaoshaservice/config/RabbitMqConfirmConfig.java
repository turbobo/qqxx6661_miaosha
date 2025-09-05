package cn.monitor4all.miaoshaservice.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ确认机制配置类
 */
@Configuration
public class RabbitMqConfirmConfig {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(RabbitMqConfirmConfig.class);
    
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        
        // 设置发布确认回调
        rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
            if (ack) {
                LOGGER.info("消息发送成功，correlationData: {}", correlationData);
            } else {
                LOGGER.error("消息发送失败，correlationData: {}, 失败原因: {}", correlationData, cause);
            }
        });
        
        // 设置返回回调
        rabbitTemplate.setReturnCallback((message, replyCode, replyText, exchange, routingKey) -> {
            LOGGER.error("消息被退回，exchange[{}], routingKey[{}], replyCode[{}], replyText[{}], message[{}]", 
                exchange, routingKey, replyCode, replyText, message);
        });
        
        return rabbitTemplate;
    }
}