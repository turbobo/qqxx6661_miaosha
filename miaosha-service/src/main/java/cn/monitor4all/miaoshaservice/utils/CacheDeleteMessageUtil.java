package cn.monitor4all.miaoshaservice.utils;

import cn.monitor4all.miaoshaservice.service.CacheDeleteMessageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;

/**
 * 缓存删除消息工具类
 * 提供独立的缓存删除消息发送功能
 */
@Component
public class CacheDeleteMessageUtil {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(CacheDeleteMessageUtil.class);
    
    @Resource
    private CacheDeleteMessageService cacheDeleteMessageService;
    
    /**
     * 发送缓存删除消息
     *
     * @param cacheKey 缓存键
     * @param reason 删除原因
     * @return 是否发送成功
     */
    public boolean sendCacheDeleteMessage(String cacheKey, String reason) {
        try {
            // 检查消息队列服务是否可用
            if (!isMessageQueueAvailable()) {
                LOGGER.debug("消息队列服务不可用，跳过发送缓存删除消息，缓存键: {}, 原因: {}", cacheKey, reason);
                return false;
            }
            
            // 发送缓存删除消息
            boolean success = cacheDeleteMessageService.sendCacheDeleteMessage(cacheKey, reason);
            
            // 记录发送结果
            if (success) {
                LOGGER.debug("缓存删除消息发送成功，缓存键: {}, 原因: {}", cacheKey, reason);
            } else {
                LOGGER.warn("缓存删除消息发送失败，缓存键: {}, 原因: {}", cacheKey, reason);
            }
            
            return success;
            
        } catch (Exception e) {
            LOGGER.warn("发送缓存删除消息异常，缓存键: {}, 原因: {}, 错误: {}", cacheKey, reason, e.getMessage());
            return false;
        }
    }
    
    /**
     * 发送延迟缓存删除消息
     *
     * @param cacheKey 缓存键
     * @param reason 删除原因
     * @param delaySeconds 延迟时间（秒）
     * @return 是否发送成功
     */
    public boolean sendDelayedCacheDeleteMessage(String cacheKey, String reason, long delaySeconds) {
        try {
            // 检查消息队列服务是否可用
            if (!isMessageQueueAvailable()) {
                LOGGER.debug("消息队列服务不可用，跳过发送延迟缓存删除消息，缓存键: {}, 原因: {}, 延迟: {}秒", 
                    cacheKey, reason, delaySeconds);
                return false;
            }
            
            // 发送延迟缓存删除消息
            boolean success = cacheDeleteMessageService.sendDelayedCacheDeleteMessage(cacheKey, reason, delaySeconds);
            
            // 记录发送结果
            if (success) {
                LOGGER.debug("延迟缓存删除消息发送成功，缓存键: {}, 原因: {}, 延迟: {}秒", cacheKey, reason, delaySeconds);
            } else {
                LOGGER.warn("延迟缓存删除消息发送失败，缓存键: {}, 原因: {}, 延迟: {}秒", cacheKey, reason, delaySeconds);
            }
            
            return success;
            
        } catch (Exception e) {
            LOGGER.warn("发送延迟缓存删除消息异常，缓存键: {}, 原因: {}, 延迟: {}秒, 错误: {}", 
                cacheKey, reason, delaySeconds, e.getMessage());
            return false;
        }
    }
    
    /**
     * 批量发送缓存删除消息
     *
     * @param cacheKeys 缓存键列表
     * @param reason 删除原因
     * @return 成功发送的消息数量
     */
    public int sendBatchCacheDeleteMessage(List<String> cacheKeys, String reason) {
        try {
            // 检查消息队列服务是否可用
            if (!isMessageQueueAvailable()) {
                LOGGER.debug("消息队列服务不可用，跳过批量发送缓存删除消息，缓存键数量: {}, 原因: {}", 
                    cacheKeys != null ? cacheKeys.size() : 0, reason);
                return 0;
            }
            
            // 发送批量缓存删除消息
            int successCount = cacheDeleteMessageService.sendBatchCacheDeleteMessage(cacheKeys, reason);
            
            // 记录发送结果
            if (cacheKeys != null) {
                LOGGER.debug("批量缓存删除消息发送完成，总数: {}, 成功: {}, 原因: {}", 
                    cacheKeys.size(), successCount, reason);
            }
            
            return successCount;
            
        } catch (Exception e) {
            LOGGER.warn("批量发送缓存删除消息异常，缓存键数量: {}, 原因: {}, 错误: {}", 
                cacheKeys != null ? cacheKeys.size() : 0, reason, e.getMessage());
            return 0;
        }
    }
    
    /**
     * 检查消息队列服务是否可用
     *
     * @return 是否可用
     */
    public boolean isMessageQueueAvailable() {
        try {
            return cacheDeleteMessageService != null && cacheDeleteMessageService.isConnected();
        } catch (Exception e) {
            LOGGER.warn("检查消息队列服务状态失败: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * 发送票券缓存删除消息
     * 专门用于票券相关的缓存删除
     *
     * @param date 票券日期
     * @param reason 删除原因
     * @return 是否发送成功
     */
    public boolean sendTicketCacheDeleteMessage(String date, String reason) {
        return sendCacheDeleteMessage(date, reason);
    }
    
    /**
     * 发送票券延迟缓存删除消息
     * 专门用于票券相关的延迟缓存删除
     *
     * @param date 票券日期
     * @param reason 删除原因
     * @param delaySeconds 延迟时间（秒）
     * @return 是否发送成功
     */
    public boolean sendTicketDelayedCacheDeleteMessage(String date, String reason, long delaySeconds) {
        return sendDelayedCacheDeleteMessage(date, reason, delaySeconds);
    }
    
    /**
     * 批量发送票券缓存删除消息
     * 专门用于票券相关的批量缓存删除
     *
     * @param dates 票券日期列表
     * @param reason 删除原因
     * @return 成功发送的消息数量
     */
    public int sendTicketBatchCacheDeleteMessage(List<String> dates, String reason) {
        return sendBatchCacheDeleteMessage(dates, reason);
    }
}
