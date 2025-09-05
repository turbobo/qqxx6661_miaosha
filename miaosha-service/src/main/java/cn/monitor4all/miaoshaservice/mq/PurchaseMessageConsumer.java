package cn.monitor4all.miaoshaservice.mq;

import cn.monitor4all.miaoshadao.model.PurchaseRequest;
import cn.monitor4all.miaoshaservice.config.RabbitMqPurchaseConfig;
import cn.monitor4all.miaoshaservice.service.TicketService;
import com.alibaba.fastjson.JSON;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 异步抢购消息消费者
 */
@Component
public class PurchaseMessageConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(PurchaseMessageConsumer.class);
    
    // 添加消息计数器，用于跟踪接收的消息数量
    private static final AtomicInteger messageCounter = new AtomicInteger(0);

    @Resource
    private TicketService ticketService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 消费异步抢购消息
     *
     * @param message 消息内容
     */
    @RabbitListener(queues = RabbitMqPurchaseConfig.MIAOSHA_PURCHASE_QUEUE, concurrency = "10")
    public void handlePurchaseMessage(Map<String, Object> message,
                                    @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
                                    Channel channel) {
        int count = messageCounter.incrementAndGet();
        try {
            LOGGER.info("收到异步抢购消息，请求: {}, 线程: {}, 消息计数: {}", 
                JSON.toJSONString(message), Thread.currentThread().getName(), count);

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

            LOGGER.info("开始处理异步抢购请求，请求ID: {}, 用户ID: {}, 日期: {}, 消息计数: {}", 
                requestId, userId, date, count);

            // 调用乐观锁抢购方法
            ticketService.asyncPurchaseTicketWithOptimisticLock(request);

            // 手动确认消息
            channel.basicAck(deliveryTag, false);
            LOGGER.info("异步抢购处理完成，请求ID: {}, 消息计数: {}", requestId, count);

        } catch (Exception e) {
            LOGGER.error("处理异步抢购消息失败，消息计数: {}, 错误: {}", count, e.getMessage(), e);
            try {
                // 消息重新入队以便重试
                channel.basicNack(deliveryTag, false, true);
            } catch (IOException ioException) {
                LOGGER.error("确认消息失败失败，消息计数: {}, 错误: {}", count, ioException.getMessage(), ioException);
            }
        }
    }
}