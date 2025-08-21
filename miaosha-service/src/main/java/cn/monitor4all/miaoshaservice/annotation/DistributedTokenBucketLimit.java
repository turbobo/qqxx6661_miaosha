package cn.monitor4all.miaoshaservice.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 分布式令牌桶限流注解
 * 用于方法级别的限流控制
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DistributedTokenBucketLimit {
    
    /**
     * 限流键（支持SpEL表达式）
     * 默认使用方法名作为限流键
     */
    String key() default "";
    
    /**
     * 桶容量
     * 默认从配置文件中读取
     */
    int capacity() default -1;
    
    /**
     * 令牌填充速率（每秒）
     * 默认从配置文件中读取
     */
    double rate() default -1.0;
    
    /**
     * 请求令牌数
     * 默认每次请求消耗1个令牌
     */
    int tokens() default 1;
    
    /**
     * 是否阻塞等待
     * 默认非阻塞模式
     */
    boolean blocking() default false;
    
    /**
     * 阻塞等待超时时间（毫秒）
     * 仅在blocking=true时生效
     */
    long timeout() default 1000;
    
    /**
     * 限流策略
     */
    Strategy strategy() default Strategy.INTERFACE;
    
    /**
     * 限流策略枚举
     */
    enum Strategy {
        /**
         * 接口级别限流（基于方法名或自定义key）
         */
        INTERFACE,
        
        /**
         * 用户级别限流（基于用户ID）
         */
        USER,
        
        /**
         * 全局级别限流（基于全局计数器）
         */
        GLOBAL,
        
        /**
         * 自定义限流（基于SpEL表达式）
         */
        CUSTOM
    }
    
    /**
     * 限流失败时的错误消息
     */
    String message() default "请求频率超限，请稍后再试";
    
    /**
     * 限流失败时的错误代码
     */
    String errorCode() default "RATE_LIMIT_EXCEEDED";
    
    /**
     * 是否记录限流日志
     */
    boolean logLimit() default true;
    
    /**
     * 是否启用预热
     * 默认启用，系统启动时会预热令牌桶
     */
    boolean enableWarmup() default true;
}
