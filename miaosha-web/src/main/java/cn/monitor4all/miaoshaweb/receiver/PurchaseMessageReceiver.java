package cn.monitor4all.miaoshaweb.receiver;

import cn.monitor4all.miaoshaweb.model.PurchaseMessage;
import cn.monitor4all.miaoshaweb.service.PurchaseMessageService;
import com.alibaba.fastjson.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 抢购消息队列消费者
 * 负责接收和处理抢购消息
 */
@Component
public class PurchaseMessageReceiver {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(PurchaseMessageReceiver.class);
    
    @Resource
    private PurchaseMessageService purchaseMessageService;
    
    /**
     * 监听抢购消息队列
     * 
     * @param message 消息内容（JSON字符串）
     */
    @RabbitListener(queues = "${purchase.message.queue:purchase.queue}")
    public void handlePurchaseMessage(String message) {
        try {
            LOGGER.info("收到抢购消息: {}", message);
            
            // 解析消息
            PurchaseMessage purchaseMessage = JSON.parseObject(message, PurchaseMessage.class);
            if (purchaseMessage == null) {
                LOGGER.error("消息解析失败，消息内容: {}", message);
                return;
            }
            
            LOGGER.info("开始处理抢购消息，消息ID: {}, 用户ID: {}, 日期: {}", 
                purchaseMessage.getMessageId(), purchaseMessage.getUserId(), purchaseMessage.getDate());
            
            // 处理抢购消息
            purchaseMessageService.processPurchaseMessage(purchaseMessage);
            
            LOGGER.info("抢购消息处理完成，消息ID: {}", purchaseMessage.getMessageId());
            
        } catch (Exception e) {
            LOGGER.error("处理抢购消息异常，消息内容: {}, 错误: {}", message, e.getMessage(), e);
            
            // 这里可以添加重试逻辑或死信队列处理
            // 对于关键业务，建议实现重试机制
        }
    }
    
    /**
     * 监听延迟抢购消息队列（可选功能）
     * 
     * @param message 消息内容
     */
    @RabbitListener(queues = "${purchase.message.delayed.queue:purchase.delayed.queue}")
    public void handleDelayedPurchaseMessage(String message) {
        try {
            LOGGER.info("收到延迟抢购消息: {}", message);
            
            // 解析消息
            PurchaseMessage purchaseMessage = JSON.parseObject(message, PurchaseMessage.class);
            if (purchaseMessage == null) {
                LOGGER.error("延迟消息解析失败，消息内容: {}", message);
                return;
            }
            
            LOGGER.info("开始处理延迟抢购消息，消息ID: {}, 用户ID: {}, 日期: {}", 
                purchaseMessage.getMessageId(), purchaseMessage.getUserId(), purchaseMessage.getDate());
            
            // 处理延迟抢购消息
            purchaseMessageService.processPurchaseMessage(purchaseMessage);
            
            LOGGER.info("延迟抢购消息处理完成，消息ID: {}", purchaseMessage.getMessageId());
            
        } catch (Exception e) {
            LOGGER.error("处理延迟抢购消息异常，消息内容: {}, 错误: {}", message, e.getMessage(), e);
        }
    }
}
