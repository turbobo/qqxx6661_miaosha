package cn.monitor4all.miaoshaservice.mq;

import cn.monitor4all.miaoshadao.model.PurchaseRequest;
import cn.monitor4all.miaoshaservice.config.RabbitMqPurchaseConfig;
import cn.monitor4all.miaoshaservice.service.TicketService;
import com.alibaba.fastjson.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Map;

/**
 * 异步抢购消息消费者
 */
@Component
public class PurchaseMessageConsumer {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(PurchaseMessageConsumer.class);
    
    @Resource
    private TicketService ticketService;
    
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    
    /**
     * 消费异步抢购消息
     * @param message 消息内容
     */
    @RabbitListener(queues = RabbitMqPurchaseConfig.MIAOSHA_PURCHASE_QUEUE)
    public void handlePurchaseMessage(Map<String, Object> message) {
        try {
            LOGGER.info("收到异步抢购消息: {}", JSON.toJSONString(message));
            
            // 提取消息内容
            String requestId = (String) message.get("requestId");
            Long userId = Long.valueOf(message.get("userId").toString());
            String date = (String) message.get("date");
            String verifyHash = (String) message.get("verifyHash");
            Long timestamp = (Long) message.get("timestamp");
            
            // 将请求时间存储到Redis，用于超时检查
            stringRedisTemplate.opsForValue().set("request_time:" + requestId, String.valueOf(timestamp));
            
            // 构造PurchaseRequest对象
            PurchaseRequest request = new PurchaseRequest();
            request.setUserId(userId);
            request.setDate(date);
            request.setVerifyHash(verifyHash);
            
            LOGGER.info("开始处理异步抢购请求，请求ID: {}, 用户ID: {}, 日期: {}", requestId, userId, date);
            
            // 调用乐观锁抢购方法
            ticketService.asyncPurchaseTicketWithOptimisticLock(request);
            
            LOGGER.info("异步抢购处理完成，请求ID: {}", requestId);
            
        } catch (Exception e) {
            LOGGER.error("处理异步抢购消息失败: {}", e.getMessage(), e);
        }
    }
}
