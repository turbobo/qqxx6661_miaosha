package cn.monitor4all.miaoshaservice.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ缓存删除队列配置类
 * 配置延迟队列和交换机
 */
@Configuration
public class RabbitMqCacheDeleteConfig {
    
    // 交换机名称
    public static final String CACHE_DELETE_EXCHANGE = "cache.delete.exchange";
    
    // 立即删除队列名称
    public static final String CACHE_DELETE_QUEUE = "cache.delete.queue";
    
    // 延迟删除队列名称
    public static final String CACHE_DELETE_DELAY_QUEUE = "cache.delete.delay.queue";
    
    // 路由键
    public static final String CACHE_DELETE_ROUTING_KEY = "cache.delete";
    public static final String CACHE_DELETE_DELAY_ROUTING_KEY = "cache.delete.delay";
    
    /**
     * 缓存删除交换机
     */
    @Bean
    public DirectExchange cacheDeleteExchange() {
        return new DirectExchange(CACHE_DELETE_EXCHANGE, true, false);
    }
    
    /**
     * 立即删除队列
     */
    @Bean
    public Queue cacheDeleteQueue() {
        return QueueBuilder.durable(CACHE_DELETE_QUEUE)
                .withArgument("x-message-ttl", 0) // 立即处理
                .build();
    }
    
    /**
     * 延迟删除队列
     */
    @Bean
    public Queue cacheDeleteDelayQueue() {
        return QueueBuilder.durable(CACHE_DELETE_DELAY_QUEUE)
                .withArgument("x-message-ttl", 0) // 延迟时间由消息属性控制
                .build();
    }
    
    /**
     * 立即删除队列绑定
     */
    @Bean
    public Binding cacheDeleteBinding() {
        return BindingBuilder.bind(cacheDeleteQueue())
                .to(cacheDeleteExchange())
                .with(CACHE_DELETE_ROUTING_KEY);
    }
    
    /**
     * 延迟删除队列绑定
     */
    @Bean
    public Binding cacheDeleteDelayBinding() {
        return BindingBuilder.bind(cacheDeleteDelayQueue())
                .to(cacheDeleteExchange())
                .with(CACHE_DELETE_DELAY_ROUTING_KEY);
    }
}
