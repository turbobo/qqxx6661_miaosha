package cn.monitor4all.miaoshaservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 缓存删除线程池配置类
 * 配置专门的线程池用于缓存删除操作
 */
@Configuration
public class CacheDeleteThreadPoolConfig {
    
    @Value("${cache.delete.thread-pool.core-size:2}")
    private int corePoolSize;
    
    @Value("${cache.delete.thread-pool.max-size:4}")
    private int maxPoolSize;
    
    @Value("${cache.delete.thread-pool.queue-capacity:20}")
    private int queueCapacity;
    
    @Value("${cache.delete.thread-pool.keep-alive-seconds:60}")
    private int keepAliveSeconds;
    
    @Value("${cache.delete.thread-pool.thread-name-prefix:cache-delete-}")
    private String threadNamePrefix;
    
    /**
     * 缓存删除专用线程池
     * 用于异步删除缓存操作
     */
    @Bean(name = "cacheDeleteExecutor")
    public ThreadPoolTaskExecutor cacheDeleteExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        // 核心线程数
        executor.setCorePoolSize(corePoolSize);
        
        // 最大线程数
        executor.setMaxPoolSize(maxPoolSize);
        
        // 队列容量
        executor.setQueueCapacity(queueCapacity);
        
        // 线程空闲时间
        executor.setKeepAliveSeconds(keepAliveSeconds);
        
        // 线程名前缀
        executor.setThreadNamePrefix(threadNamePrefix);
        
        // 拒绝策略：调用者运行（确保任务不丢失）
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        
        // 等待所有任务结束后再关闭线程池
        executor.setWaitForTasksToCompleteOnShutdown(true);
        
        // 等待时间
        executor.setAwaitTerminationSeconds(60);
        
        // 初始化
        executor.initialize();
        
        return executor;
    }
}
