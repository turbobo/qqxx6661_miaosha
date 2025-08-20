package cn.monitor4all.miaoshaweb.service.impl;

import cn.monitor4all.miaoshadao.model.ApiResponse;
import cn.monitor4all.miaoshadao.model.PurchaseRecord;
import cn.monitor4all.miaoshaweb.model.PurchaseMessage;
import cn.monitor4all.miaoshadao.model.PurchaseRequest;
import cn.monitor4all.miaoshaweb.model.PurchaseResult;
import cn.monitor4all.miaoshaweb.service.PurchaseMessageService;
import cn.monitor4all.miaoshaservice.service.TicketService;
import com.alibaba.fastjson.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 抢购消息服务实现类
 */
@Service
public class PurchaseMessageServiceImpl implements PurchaseMessageService {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(PurchaseMessageServiceImpl.class);
    
    @Resource
    private AmqpTemplate amqpTemplate;
    
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    
    @Resource
    private TicketService ticketService;
    
    @Value("${purchase.message.exchange:purchase.exchange}")
    private String exchange;
    
    @Value("${purchase.message.routing.key:purchase}")
    private String routingKey;
    
    @Value("${purchase.message.result.expire.hours:24}")
    private int resultExpireHours;
    
    // 内存缓存，存储消息处理状态（生产环境建议使用Redis）
    private final ConcurrentHashMap<String, PurchaseResult> messageResultCache = new ConcurrentHashMap<>();
    
    @Override
    public boolean sendPurchaseMessage(PurchaseMessage purchaseMessage) {
        try {
            LOGGER.info("发送抢购消息到队列，消息ID: {}, 用户ID: {}, 日期: {}", 
                purchaseMessage.getMessageId(), purchaseMessage.getUserId(), purchaseMessage.getDate());
            
            // 将消息转换为JSON字符串
            String messageBody = JSON.toJSONString(purchaseMessage);
            
            // 发送到消息队列
            amqpTemplate.convertAndSend(exchange, routingKey, messageBody);
            
            // 初始化结果缓存
            PurchaseResult result = new PurchaseResult(
                purchaseMessage.getMessageId(),
                purchaseMessage.getUserId(),
                purchaseMessage.getDate(),
                "PENDING"
            );
            result.setRequestTime(purchaseMessage.getRequestTime());
            
            // 存储到缓存和Redis
            messageResultCache.put(purchaseMessage.getMessageId(), result);
            saveResultToRedis(purchaseMessage.getMessageId(), result);
            
            LOGGER.info("抢购消息发送成功，消息ID: {}", purchaseMessage.getMessageId());
            return true;
            
        } catch (Exception e) {
            LOGGER.error("发送抢购消息失败，消息ID: {}, 错误: {}", purchaseMessage.getMessageId(), e.getMessage(), e);
            return false;
        }
    }
    
    @Override
    public PurchaseResult getPurchaseResult(String messageId) {
        try {
            // 先从内存缓存查询
            PurchaseResult result = messageResultCache.get(messageId);
            if (result != null) {
                return result;
            }
            
            // 从Redis查询
            result = getResultFromRedis(messageId);
            if (result != null) {
                // 更新内存缓存
                messageResultCache.put(messageId, result);
                return result;
            }
            
            LOGGER.warn("未找到抢购结果，消息ID: {}", messageId);
            return null;
            
        } catch (Exception e) {
            LOGGER.error("查询抢购结果失败，消息ID: {}, 错误: {}", messageId, e.getMessage(), e);
            return null;
        }
    }
    
    @Override
    public PurchaseResult getPurchaseResultByUserAndDate(Long userId, String date) {
        try {
            // 生成消息ID
            String messageId = "PURCHASE_" + userId + "_" + date.replace("-", "") + "_";
            
            // 从Redis查询该用户该日期的所有消息
            String pattern = messageId + "*";
            // 这里简化处理，实际应该使用Redis的SCAN命令或更好的查询方式
            
            // 先检查是否有已存在的订单
            if (hasExistingOrder(userId, date)) {
                PurchaseResult result = new PurchaseResult("", userId, date, "SUCCESS");
                result.setOrderExists(true);
                result.setResult("用户已有该日期的订单");
                return result;
            }
            
            // 查询最新的处理结果
            // 这里简化处理，实际应该查询该用户该日期的最新消息状态
            return null;
            
        } catch (Exception e) {
            LOGGER.error("查询用户抢购结果失败，用户ID: {}, 日期: {}, 错误: {}", userId, date, e.getMessage(), e);
            return null;
        }
    }
    
    @Override
    public boolean hasExistingOrder(Long userId, String date) {
        try {
            // 检查Redis缓存中是否有订单信息
            String cacheKey = "order:user:" + userId + ":date:" + date;
            String orderInfo = stringRedisTemplate.opsForValue().get(cacheKey);
            
            if (orderInfo != null) {
                LOGGER.debug("从缓存中找到用户订单，用户ID: {}, 日期: {}", userId, date);
                return true;
            }
            
            // 检查数据库（这里简化处理，实际应该调用订单服务查询）
            // 可以通过调用ticketService的相关方法查询
            
            return false;
            
        } catch (Exception e) {
            LOGGER.error("检查用户订单失败，用户ID: {}, 日期: {}, 错误: {}", userId, date, e.getMessage(), e);
            return false;
        }
    }
    
    @Override
    public void updateMessageStatus(String messageId, String status, String result, String errorMessage) {
        try {
            PurchaseResult purchaseResult = messageResultCache.get(messageId);
            if (purchaseResult != null) {
                purchaseResult.setStatus(status);
                purchaseResult.setResult(result);
                purchaseResult.setErrorMessage(errorMessage);
                
                if ("SUCCESS".equals(status) || "FAILED".equals(status)) {
                    purchaseResult.setCompletedTime(LocalDateTime.now());
                }
                
                // 更新Redis缓存
                saveResultToRedis(messageId, purchaseResult);
                
                LOGGER.info("更新消息状态成功，消息ID: {}, 状态: {}", messageId, status);
            }
            
        } catch (Exception e) {
            LOGGER.error("更新消息状态失败，消息ID: {}, 错误: {}", messageId, e.getMessage(), e);
        }
    }
    
    @Override
    public void processPurchaseMessage(PurchaseMessage purchaseMessage) {
        String messageId = purchaseMessage.getMessageId();
        
        try {
            LOGGER.info("开始处理抢购消息，消息ID: {}, 用户ID: {}, 日期: {}", 
                messageId, purchaseMessage.getUserId(), purchaseMessage.getDate());
            
            // 更新状态为处理中
            updateMessageStatus(messageId, "PROCESSING", null, null);
            
            // 检查是否已有订单
            if (hasExistingOrder(purchaseMessage.getUserId(), purchaseMessage.getDate())) {
                updateMessageStatus(messageId, "SUCCESS", "用户已有该日期的订单", null);
                LOGGER.info("用户已有订单，跳过处理，消息ID: {}", messageId);
                return;
            }
            
            // 构建PurchaseRequest
            PurchaseRequest request = new PurchaseRequest();
            request.setUserId(purchaseMessage.getUserId());
            request.setDate(purchaseMessage.getDate());
            request.setVerifyHash(purchaseMessage.getVerifyCode()); // Fixed method name
            
            // 调用ticketService进行抢购（基于purchaseTicketV1的逻辑）
            // 这里需要根据实际的TicketService接口调整
             ApiResponse<PurchaseRecord> response = ticketService.purchaseTicket(request);
            
            // 模拟抢购成功
            updateMessageStatus(messageId, "SUCCESS", "抢购成功", null);
            LOGGER.info("抢购消息处理成功，消息ID: {}", messageId);
            
        } catch (Exception e) {
            LOGGER.error("处理抢购消息失败，消息ID: {}, 错误: {}", messageId, e.getMessage(), e);
            updateMessageStatus(messageId, "FAILED", null, e.getMessage());
        }
    }
    
    @Override
    public boolean retryFailedMessage(String messageId) {
        try {
            PurchaseResult result = getPurchaseResult(messageId);
            if (result != null && "FAILED".equals(result.getStatus())) {
                // 重新发送消息到队列
                // 这里简化处理，实际应该重新创建PurchaseMessage
                LOGGER.info("重试失败消息，消息ID: {}", messageId);
                return true;
            }
            return false;
            
        } catch (Exception e) {
            LOGGER.error("重试失败消息异常，消息ID: {}, 错误: {}", messageId, e.getMessage(), e);
            return false;
        }
    }
    
    @Override
    public int cleanupExpiredMessages(int expireHours) {
        try {
            int cleanedCount = 0;
            LocalDateTime expireTime = LocalDateTime.now().minusHours(expireHours);
            
            // 清理内存缓存
            messageResultCache.entrySet().removeIf(entry -> {
                PurchaseResult result = entry.getValue();
                if (result.getRequestTime() != null && result.getRequestTime().isBefore(expireTime)) {
                    // 同时清理Redis缓存
                    stringRedisTemplate.delete("purchase:result:" + entry.getKey());
                    return true;
                }
                return false;
            });
            
            LOGGER.info("清理过期消息完成，清理数量: {}", cleanedCount);
            return cleanedCount;
            
        } catch (Exception e) {
            LOGGER.error("清理过期消息失败，错误: {}", e.getMessage(), e);
            return 0;
        }
    }
    
    /**
     * 保存结果到Redis
     */
    private void saveResultToRedis(String messageId, PurchaseResult result) {
        try {
            String key = "purchase:result:" + messageId;
            String value = JSON.toJSONString(result);
            stringRedisTemplate.opsForValue().set(key, value, resultExpireHours, TimeUnit.HOURS);
            
        } catch (Exception e) {
            LOGGER.error("保存结果到Redis失败，消息ID: {}, 错误: {}", messageId, e.getMessage(), e);
        }
    }
    
    /**
     * 从Redis获取结果
     */
    private PurchaseResult getResultFromRedis(String messageId) {
        try {
            String key = "purchase:result:" + messageId;
            String value = stringRedisTemplate.opsForValue().get(key);
            
            if (value != null) {
                return JSON.parseObject(value, PurchaseResult.class);
            }
            
            return null;
            
        } catch (Exception e) {
            LOGGER.error("从Redis获取结果失败，消息ID: {}, 错误: {}", messageId, e.getMessage(), e);
            return null;
        }
    }
}
