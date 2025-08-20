package cn.monitor4all.miaoshaservice.service.impl;

import cn.monitor4all.miaoshaservice.service.CacheDeleteMessageService;
import com.alibaba.fastjson.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 缓存删除消息队列服务实现类
 * 使用RabbitMQ发送缓存删除消息
 */
@Service
public class CacheDeleteMessageServiceImpl implements CacheDeleteMessageService {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(CacheDeleteMessageServiceImpl.class);
    
    @Resource
    private AmqpTemplate amqpTemplate;
    
    @Value("${cache.delete.exchange:cache.delete.exchange}")
    private String exchange;
    
    @Value("${cache.delete.routing.key:cache.delete}")
    private String routingKey;
    
    @Value("${cache.delete.delayed.exchange:cache.delete.delayed.exchange}")
    private String delayedExchange;
    
    @Value("${cache.delete.delayed.routing.key:cache.delete.delayed}")
    private String delayedRoutingKey;
    
    @Override
    public boolean sendCacheDeleteMessage(String cacheKey, String reason) {
        try {
            // 构建消息内容
            Map<String, Object> messageContent = new HashMap<>();
            messageContent.put("cacheKey", cacheKey);
            messageContent.put("reason", reason);
            messageContent.put("timestamp", System.currentTimeMillis());
            messageContent.put("type", "IMMEDIATE_DELETE");
            
            // 转换为JSON字符串
            String messageBody = JSON.toJSONString(messageContent);
            
            // 发送消息
            amqpTemplate.convertAndSend(exchange, routingKey, messageBody);
            
            LOGGER.info("缓存删除消息发送成功，缓存键: {}, 原因: {}, 消息内容: {}", 
                cacheKey, reason, messageBody);
            
            return true;
            
        } catch (Exception e) {
            LOGGER.error("缓存删除消息发送失败，缓存键: {}, 原因: {}, 错误: {}", 
                cacheKey, reason, e.getMessage(), e);
            return false;
        }
    }
    
    @Override
    public boolean sendDelayedCacheDeleteMessage(String cacheKey, String reason, long delaySeconds) {
        try {
            // 构建消息内容
            Map<String, Object> messageContent = new HashMap<>();
            messageContent.put("cacheKey", cacheKey);
            messageContent.put("reason", reason);
            messageContent.put("timestamp", System.currentTimeMillis());
            messageContent.put("type", "DELAYED_DELETE");
            messageContent.put("delaySeconds", delaySeconds);
            
            // 转换为JSON字符串
            String messageBody = JSON.toJSONString(messageContent);
            
            // 发送延迟消息
            amqpTemplate.convertAndSend(delayedExchange, delayedRoutingKey, messageBody);
            
            LOGGER.info("延迟缓存删除消息发送成功，缓存键: {}, 原因: {}, 延迟: {}秒, 消息内容: {}", 
                cacheKey, reason, delaySeconds, messageBody);
            
            return true;
            
        } catch (Exception e) {
            LOGGER.error("延迟缓存删除消息发送失败，缓存键: {}, 原因: {}, 延迟: {}秒, 错误: {}", 
                cacheKey, reason, delaySeconds, e.getMessage(), e);
            return false;
        }
    }
    
    @Override
    public int sendBatchCacheDeleteMessage(List<String> cacheKeys, String reason) {
        if (cacheKeys == null || cacheKeys.isEmpty()) {
            LOGGER.warn("缓存键列表为空，跳过批量发送");
            return 0;
        }
        
        int successCount = 0;
        for (String cacheKey : cacheKeys) {
            try {
                if (sendCacheDeleteMessage(cacheKey, reason)) {
                    successCount++;
                }
            } catch (Exception e) {
                LOGGER.error("批量发送缓存删除消息失败，缓存键: {}, 错误: {}", cacheKey, e.getMessage());
            }
        }
        
        LOGGER.info("批量缓存删除消息发送完成，总数: {}, 成功: {}, 失败: {}", 
            cacheKeys.size(), successCount, cacheKeys.size() - successCount);
        
        return successCount;
    }
    
    @Override
    public boolean isConnected() {
        try {
            // 检查AMQP连接状态
            return amqpTemplate != null;
        } catch (Exception e) {
            LOGGER.warn("检查AMQP连接状态失败: {}", e.getMessage());
            return false;
        }
    }
}
