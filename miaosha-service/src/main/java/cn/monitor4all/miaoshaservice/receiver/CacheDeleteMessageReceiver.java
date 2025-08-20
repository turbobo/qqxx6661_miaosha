package cn.monitor4all.miaoshaservice.receiver;

import cn.monitor4all.miaoshaservice.service.TicketCacheManager;
import com.alibaba.fastjson.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Map;

/**
 * 缓存删除消息队列消费者
 * 接收并处理缓存删除消息，确保缓存删除的可靠性
 */
@Component
public class CacheDeleteMessageReceiver {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(CacheDeleteMessageReceiver.class);
    
    @Resource
    private TicketCacheManager ticketCacheManager;
    
    /**
     * 处理立即删除缓存消息
     *
     * @param message 消息内容
     */
    public void handleCacheDeleteMessage(String message) {
        try {
            LOGGER.info("收到缓存删除消息: {}", message);
            
            // 解析消息内容
            Map<String, Object> messageContent = JSON.parseObject(message, Map.class);
            String cacheKey = (String) messageContent.get("cacheKey");
            String reason = (String) messageContent.get("reason");
            String type = (String) messageContent.get("type");
            
            LOGGER.info("开始处理缓存删除消息，缓存键: {}, 原因: {}, 类型: {}", cacheKey, reason, type);
            
            // 执行缓存删除
            boolean success = deleteCache(cacheKey, reason);
            
            if (success) {
                LOGGER.info("缓存删除消息处理成功，缓存键: {}, 原因: {}", cacheKey, reason);
            } else {
                LOGGER.warn("缓存删除消息处理失败，缓存键: {}, 原因: {}", cacheKey, reason);
            }
            
        } catch (Exception e) {
            LOGGER.error("处理缓存删除消息异常: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 处理延迟删除缓存消息
     *
     * @param message 消息内容
     */
    public void handleDelayedCacheDeleteMessage(String message) {
        try {
            LOGGER.info("收到延迟缓存删除消息: {}", message);
            
            // 解析消息内容
            Map<String, Object> messageContent = JSON.parseObject(message, Map.class);
            String cacheKey = (String) messageContent.get("cacheKey");
            String reason = (String) messageContent.get("reason");
            String type = (String) messageContent.get("type");
            Long delaySeconds = (Long) messageContent.get("delaySeconds");
            
            LOGGER.info("开始处理延迟缓存删除消息，缓存键: {}, 原因: {}, 类型: {}, 延迟: {}秒", 
                cacheKey, reason, type, delaySeconds);
            
            // 执行缓存删除
            boolean success = deleteCache(cacheKey, reason);
            
            if (success) {
                LOGGER.info("延迟缓存删除消息处理成功，缓存键: {}, 原因: {}", cacheKey, reason);
            } else {
                LOGGER.warn("延迟缓存删除消息处理失败，缓存键: {}, 原因: {}", cacheKey, reason);
            }
            
        } catch (Exception e) {
            LOGGER.error("处理延迟缓存删除消息异常: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 执行缓存删除操作
     *
     * @param cacheKey 缓存键
     * @param reason 删除原因
     * @return 是否删除成功
     */
    private boolean deleteCache(String cacheKey, String reason) {
        try {
            if (cacheKey == null || cacheKey.trim().isEmpty()) {
                LOGGER.warn("缓存键为空，跳过删除操作");
                return false;
            }
            
            // 调用缓存管理器删除缓存
            ticketCacheManager.deleteTicket(cacheKey);
            
            LOGGER.debug("缓存删除操作执行成功，缓存键: {}, 原因: {}", cacheKey, reason);
            return true;
            
        } catch (Exception e) {
            LOGGER.error("缓存删除操作执行失败，缓存键: {}, 原因: {}, 错误: {}", 
                cacheKey, reason, e.getMessage(), e);
            return false;
        }
    }
}
