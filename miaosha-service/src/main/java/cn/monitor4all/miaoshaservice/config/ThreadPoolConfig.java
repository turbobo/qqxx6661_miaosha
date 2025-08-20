package cn.monitor4all.miaoshaservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 线程池配置类
 * 用于异步执行延迟删除缓存等任务
 */
@Configuration
@EnableAsync
public class ThreadPoolConfig {

    /**
     * 缓存操作线程池
     * 专门用于执行缓存相关的异步任务，如延迟删除缓存
     */
    @Bean("cacheTaskExecutor")
    public Executor cacheTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        // 核心线程数：2个线程
        executor.setCorePoolSize(2);
        
        // 最大线程数：5个线程
        executor.setMaxPoolSize(5);
        
        // 队列容量：100个任务
        executor.setQueueCapacity(100);
        
        // 线程名前缀
        executor.setThreadNamePrefix("cache-task-");
        
        // 线程空闲时间：60秒
        executor.setKeepAliveSeconds(60);
        
        // 拒绝策略：由调用线程处理
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        
        // 等待所有任务结束后再关闭线程池
        executor.setWaitForTasksToCompleteOnShutdown(true);
        
        // 等待时间：30秒
        executor.setAwaitTerminationSeconds(30);
        
        // 初始化
        executor.initialize();
        
        return executor;
    }

    /**
     * 消息队列任务线程池
     * 用于处理消息队列相关的异步任务
     */
    @Bean("mqTaskExecutor")
    public Executor mqTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        // 核心线程数：3个线程
        executor.setCorePoolSize(3);
        
        // 最大线程数：8个线程
        executor.setMaxPoolSize(8);
        
        // 队列容量：200个任务
        executor.setQueueCapacity(200);
        
        // 线程名前缀
        executor.setThreadNamePrefix("mq-task-");
        
        // 线程空闲时间：60秒
        executor.setKeepAliveSeconds(60);
        
        // 拒绝策略：由调用线程处理
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        
        // 等待所有任务结束后再关闭线程池
        executor.setWaitForTasksToCompleteOnShutdown(true);
        
        // 等待时间：30秒
        executor.setAwaitTerminationSeconds(30);
        
        // 初始化
        executor.initialize();
        
        return executor;
    }
}
