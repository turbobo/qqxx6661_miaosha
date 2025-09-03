//package cn.monitor4all.miaoshaservice.config;
//
//import org.springframework.amqp.rabbit.connection.ConnectionFactory;
//import org.springframework.amqp.rabbit.core.RabbitTemplate;
//import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//
//@Configuration
//public class RabbitMqConfig {
//
//    @Bean
//    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
//        RabbitTemplate template = new RabbitTemplate(connectionFactory);
//        // 启用发布者确认
//        template.setConfirmCallback((correlationData, ack, cause) -> {
//            if (ack) {
//                System.out.println("消息发送成功: " + correlationData);
//            } else {
//                System.out.println("消息发送失败: " + cause);
//            }
//        });
//
//        // 启用返回回调，处理无法路由的消息
//        template.setReturnsCallback(returned -> {
//            System.out.println("消息无法路由: " + returned);
//        });
//
//        // 设置消息转换器
//        template.setMessageConverter(new Jackson2JsonMessageConverter());
//
//        return template;
//    }
//}