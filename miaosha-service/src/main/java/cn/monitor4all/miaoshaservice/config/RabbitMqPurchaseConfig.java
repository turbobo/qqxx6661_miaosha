package cn.monitor4all.miaoshaservice.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ异步抢购队列配置类
 * 配置抢购队列和交换机
 */
@Configuration
public class RabbitMqPurchaseConfig {
    
    // 交换机名称
    public static final String MIAOSHA_PURCHASE_EXCHANGE = "miaosha.purchase.exchange";
    
    // 抢购队列名称
    public static final String MIAOSHA_PURCHASE_QUEUE = "miaosha.purchase.queue";
    
    // 订单创建队列名称
    public static final String MIAOSHA_ORDER_CREATION_QUEUE = "miaosha.order.creation.queue";
    
    // 路由键
    public static final String MIAOSHA_PURCHASE_ROUTING_KEY = "miaosha.purchase.key";

    public static final String MIAOSHA_ORDER_CREATION_ROUTING_KEY = "miaosha.order.creation.key";

    /**
     * 抢购交换机
     */
    @Bean
    public DirectExchange miaoshaPurchaseExchange() {
        return new DirectExchange(MIAOSHA_PURCHASE_EXCHANGE, true, false);
    }
    
    /**
     * 抢购队列
     */
    @Bean
    public Queue miaoshaPurchaseQueue() {
        return QueueBuilder.durable(MIAOSHA_PURCHASE_QUEUE)
                .withArgument("x-message-ttl", 0) // 立即处理
                .build();
    }
    
    /**
     * 订单创建队列
     */
    @Bean
    public Queue miaoshaOrderCreationQueue() {
        return QueueBuilder.durable(MIAOSHA_ORDER_CREATION_QUEUE)
                .withArgument("x-message-ttl", 0) // 立即处理
                .build();
    }
    
    /**
     * 抢购队列绑定
     */
    @Bean
    public Binding miaoshaPurchaseBinding() {
        return BindingBuilder.bind(miaoshaPurchaseQueue())
                .to(miaoshaPurchaseExchange())
                .with(MIAOSHA_PURCHASE_ROUTING_KEY);
    }
    
    /**
     * 订单创建队列绑定
     */
    @Bean
    public Binding miaoshaOrderCreationBinding() {
        return BindingBuilder.bind(miaoshaOrderCreationQueue())
                .to(miaoshaPurchaseExchange())
                .with(MIAOSHA_ORDER_CREATION_ROUTING_KEY);
    }
}