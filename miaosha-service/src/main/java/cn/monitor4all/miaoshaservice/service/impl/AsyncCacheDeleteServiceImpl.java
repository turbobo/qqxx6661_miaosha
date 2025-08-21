package cn.monitor4all.miaoshaservice.service.impl;

import cn.monitor4all.miaoshaservice.service.AsyncCacheDeleteService;
import com.alibaba.fastjson.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * 异步缓存删除服务实现类
 * 支持线程池异步删除和队列异步删除两种方式
 */
@Service
public class AsyncCacheDeleteServiceImpl implements AsyncCacheDeleteService {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(AsyncCacheDeleteServiceImpl.class);
    
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    
    @Resource
    private AmqpTemplate amqpTemplate;
    
    @Resource(name = "cacheDeleteExecutor")
    private Executor cacheDeleteExecutor;
    
    @Value("${spring.application.name:miaosha-service}")
    private String applicationName;
    
    // 队列相关配置
    private static final String CACHE_DELETE_EXCHANGE = "cache.delete.exchange";
    private static final String CACHE_DELETE_ROUTING_KEY = "cache.delete";
    private static final String CACHE_DELETE_DELAY_ROUTING_KEY = "cache.delete.delay";
    
    // 默认延迟删除时间（毫秒）
    private static final long DEFAULT_DELAY_MILLIS = 1000;
    
    @Override
    public void deleteCacheAsync(String cacheKey) {
        deleteCacheAsync(cacheKey, 0);
    }
    
    @Override
    public void deleteCacheAsync(String cacheKey, long delayMillis) {
        try {
            if (delayMillis > 0) {
                // 延迟删除
                CompletableFuture.runAsync(() -> {
                    try {
                        Thread.sleep(delayMillis);
                        performCacheDelete(cacheKey, "线程池延迟删除");
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        LOGGER.warn("线程池延迟删除被中断，缓存键: {}", cacheKey);
                    }
                }, cacheDeleteExecutor);
            } else {
                // 立即删除
                CompletableFuture.runAsync(() -> {
                    performCacheDelete(cacheKey, "线程池立即删除");
                }, cacheDeleteExecutor);
            }
            
            LOGGER.debug("线程池异步删除缓存任务已提交，缓存键: {}, 延迟: {}ms", cacheKey, delayMillis);
            
        } catch (Exception e) {
            LOGGER.error("提交线程池异步删除任务失败，缓存键: {}, 延迟: {}ms", cacheKey, delayMillis, e);
            // 异常时使用队列删除作为兜底
            deleteCacheByQueue(cacheKey, delayMillis);
        }
    }
    
    @Override
    public void deleteCacheByQueue(String cacheKey) {
        deleteCacheByQueue(cacheKey, 0);
    }
    
    @Override
    public void deleteCacheByQueue(String cacheKey, long delayMillis) {
        try {
            // 构建删除消息
            CacheDeleteMessage deleteMessage = new CacheDeleteMessage();
            deleteMessage.setCacheKey(cacheKey);
            deleteMessage.setDelayMillis(delayMillis);
            deleteMessage.setTimestamp(System.currentTimeMillis());
            deleteMessage.setSource(applicationName);
            
                    // 发送到队列
        if (delayMillis > 0) {
            // 延迟删除，使用延迟队列
            amqpTemplate.convertAndSend(CACHE_DELETE_EXCHANGE, CACHE_DELETE_DELAY_ROUTING_KEY, 
                deleteMessage, message -> {
                    message.getMessageProperties().setDelay((int) delayMillis);
                    return message;
                });
            LOGGER.debug("延迟删除消息已发送到队列，缓存键: {}, 延迟: {}ms", cacheKey, delayMillis);
        } else {
            // 立即删除
            amqpTemplate.convertAndSend(CACHE_DELETE_EXCHANGE, CACHE_DELETE_ROUTING_KEY, deleteMessage);
            LOGGER.debug("立即删除消息已发送到队列，缓存键: {}", cacheKey);
        }
            
        } catch (Exception e) {
            LOGGER.error("发送队列删除消息失败，缓存键: {}, 延迟: {}ms", cacheKey, delayMillis, e);
            // 队列失败时，使用线程池作为兜底
            deleteCacheAsync(cacheKey, delayMillis);
        }
    }
    
    @Override
    public void deleteCacheDualAsync(String cacheKey) {
        deleteCacheDualAsync(cacheKey, 0);
    }
    
    @Override
    public void deleteCacheDualAsync(String cacheKey, long delayMillis) {
        try {
            LOGGER.info("开始双重异步删除缓存，缓存键: {}, 延迟: {}ms", cacheKey, delayMillis);
            
            // 第一步：使用线程池异步删除
            deleteCacheAsync(cacheKey, delayMillis);
            
            // 第二步：使用队列异步删除（作为双重保障）
            deleteCacheByQueue(cacheKey, delayMillis);
            
            LOGGER.info("双重异步删除缓存任务已提交，缓存键: {}, 延迟: {}ms", cacheKey, delayMillis);
            
        } catch (Exception e) {
            LOGGER.error("双重异步删除缓存失败，缓存键: {}, 延迟: {}ms", cacheKey, delayMillis, e);
            // 异常时，尝试使用队列删除作为最后的兜底
            try {
                deleteCacheByQueue(cacheKey, delayMillis);
            } catch (Exception ex) {
                LOGGER.error("队列删除兜底也失败，缓存键: {}", cacheKey, ex);
            }
        }
    }
    
    @Override
    public void deleteCacheBatchAsync(List<String> cacheKeys) {
        if (cacheKeys == null || cacheKeys.isEmpty()) {
            LOGGER.warn("批量删除缓存键列表为空");
            return;
        }
        
        try {
            LOGGER.info("开始批量异步删除缓存，数量: {}", cacheKeys.size());
            
            for (String cacheKey : cacheKeys) {
                // 对每个缓存键使用双重异步删除
                deleteCacheDualAsync(cacheKey);
            }
            
            LOGGER.info("批量异步删除缓存任务已提交，数量: {}", cacheKeys.size());
            
        } catch (Exception e) {
            LOGGER.error("批量异步删除缓存失败，数量: {}", cacheKeys.size(), e);
            // 异常时，尝试逐个删除
            for (String cacheKey : cacheKeys) {
                try {
                    deleteCacheByQueue(cacheKey);
                } catch (Exception ex) {
                    LOGGER.error("单个缓存键删除失败: {}", cacheKey, ex);
                }
            }
        }
    }
    
    /**
     * 执行实际的缓存删除操作
     * @param cacheKey 缓存键
     * @param deleteMethod 删除方式描述
     */
    private void performCacheDelete(String cacheKey, String deleteMethod) {
        try {
            long startTime = System.currentTimeMillis();
            
            Boolean deleted = stringRedisTemplate.delete(cacheKey);
            
            long costTime = System.currentTimeMillis() - startTime;
            
            if (Boolean.TRUE.equals(deleted)) {
                LOGGER.info("{}成功，缓存键: {}, 耗时: {}ms", deleteMethod, cacheKey, costTime);
            } else {
                LOGGER.debug("{}完成，缓存键: {} (可能不存在)，耗时: {}ms", deleteMethod, cacheKey, costTime);
            }
            
        } catch (Exception e) {
            LOGGER.error("{}失败，缓存键: {}", deleteMethod, cacheKey, e);
            // 删除失败时，尝试使用队列删除作为兜底
            try {
                deleteCacheByQueue(cacheKey);
            } catch (Exception ex) {
                LOGGER.error("队列删除兜底也失败，缓存键: {}", cacheKey, ex);
            }
        }
    }
    
    /**
     * 缓存删除消息
     */
    public static class CacheDeleteMessage {
        private String cacheKey;
        private long delayMillis;
        private long timestamp;
        private String source;
        
        // getters and setters
        public String getCacheKey() { return cacheKey; }
        public void setCacheKey(String cacheKey) { this.cacheKey = cacheKey; }
        
        public long getDelayMillis() { return delayMillis; }
        public void setDelayMillis(long delayMillis) { this.delayMillis = delayMillis; }
        
        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
        
        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }
        
        @Override
        public String toString() {
            return JSON.toJSONString(this);
        }
    }
}
