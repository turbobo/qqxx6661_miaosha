package cn.monitor4all.miaoshaservice.receiver;

import cn.monitor4all.miaoshaservice.service.impl.AsyncCacheDeleteServiceImpl;
import com.alibaba.fastjson.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 缓存删除队列消息接收器
 * 处理来自队列的缓存删除消息
 */
@Component
public class CacheDeleteReceiver {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(CacheDeleteReceiver.class);
    
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    
    /**
     * 处理立即删除缓存消息
     */
    @RabbitListener(queues = "cache.delete.queue")
    public void handleCacheDelete(String message) {
        try {
            LOGGER.info("收到立即删除缓存消息: {}", message);
            
            AsyncCacheDeleteServiceImpl.CacheDeleteMessage deleteMessage = 
                JSON.parseObject(message, AsyncCacheDeleteServiceImpl.CacheDeleteMessage.class);
            
            if (deleteMessage != null && deleteMessage.getCacheKey() != null) {
                // 执行缓存删除
                performCacheDelete(deleteMessage.getCacheKey(), "队列立即删除");
            } else {
                LOGGER.warn("无效的缓存删除消息: {}", message);
            }
            
        } catch (Exception e) {
            LOGGER.error("处理立即删除缓存消息失败: {}", message, e);
        }
    }
    
    /**
     * 处理延迟删除缓存消息
     * 注意：延迟队列的消息已经延迟了指定时间，这里直接处理即可
     */
    @RabbitListener(queues = "cache.delete.delay.queue")
    public void handleCacheDeleteDelay(String message) {
        try {
            LOGGER.info("收到延迟删除缓存消息: {}", message);
            
            AsyncCacheDeleteServiceImpl.CacheDeleteMessage deleteMessage = 
                JSON.parseObject(message, AsyncCacheDeleteServiceImpl.CacheDeleteMessage.class);
            
            if (deleteMessage != null && deleteMessage.getCacheKey() != null) {
                LOGGER.info("开始执行延迟删除缓存，缓存键: {}, 原始延迟: {}ms", 
                    deleteMessage.getCacheKey(), deleteMessage.getDelayMillis());
                
                // 延迟队列的消息已经延迟了指定时间，这里直接执行删除
                performCacheDelete(deleteMessage.getCacheKey(), "队列延迟删除");
                
            } else {
                LOGGER.warn("无效的延迟删除缓存消息: {}", message);
            }
            
        } catch (Exception e) {
            LOGGER.error("处理延迟删除缓存消息失败: {}", message, e);
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
            
            LOGGER.info("{}开始执行，缓存键: {}", deleteMethod, cacheKey);
            
            // 执行实际的Redis删除操作
            Boolean deleted = stringRedisTemplate.delete(cacheKey);
            
            long costTime = System.currentTimeMillis() - startTime;
            
            if (Boolean.TRUE.equals(deleted)) {
                LOGGER.info("{}成功，缓存键: {}, 耗时: {}ms", deleteMethod, cacheKey, costTime);
            } else {
                LOGGER.debug("{}完成，缓存键: {} (可能不存在)，耗时: {}ms", deleteMethod, cacheKey, costTime);
            }
            
        } catch (Exception e) {
            LOGGER.error("{}失败，缓存键: {}", deleteMethod, cacheKey, e);
        }
    }
}
