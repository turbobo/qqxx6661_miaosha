package cn.monitor4all.miaoshaservice.mq;

import cn.monitor4all.miaoshadao.dao.TicketEntity;
import cn.monitor4all.miaoshadao.dao.TicketOrder;
import cn.monitor4all.miaoshadao.mapper.TicketEntityMapper;
import cn.monitor4all.miaoshadao.mapper.TicketOrderMapper;
import cn.monitor4all.miaoshadao.model.PurchaseRecord;
import java.io.IOException;
import cn.monitor4all.miaoshaservice.config.RabbitMqPurchaseConfig;
import cn.monitor4all.miaoshaservice.service.TicketCacheManager;
import cn.monitor4all.miaoshaservice.service.TicketCodeGeneratorService;
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
import java.time.LocalDate;
import java.util.Date;
import java.util.Map;

/**
 * 订单创建消费者
 * 负责从消息队列中接收订单创建任务并执行
 */
@Component
public class OrderCreationConsumer {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(OrderCreationConsumer.class);
    
    @Resource
    private TicketOrderMapper ticketOrderMapper;
    
    @Resource
    private TicketCodeGeneratorService ticketCodeGeneratorService;
    
    @Resource
    private TicketCacheManager ticketCacheManager;
    
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private TicketEntityMapper ticketEntityMapper;
    
    /**
     * 消费订单创建消息
     * @param message 消息内容
     * @param deliveryTag 消息标签，用于手动确认
     */
    @RabbitListener(queues = RabbitMqPurchaseConfig.MIAOSHA_ORDER_CREATION_QUEUE, concurrency = "5")
    public void handleOrderCreationMessage(Map<String, Object> message,
                                           @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
                                           Channel channel) {

        try {
            LOGGER.info("收到订单创建消息: {}", message);
            
            // 解析消息内容
            String requestId = (String) message.get("requestId");
            Long userId = Long.valueOf(message.get("userId").toString());
            String purchaseDate = (String) message.get("date");
            String verifyHash = (String) message.get("verifyHash");
            Long timestamp = (Long) message.get("timestamp");
            
            LOGGER.info("开始处理订单创建任务，用户ID: {}, 日期: {}", userId, purchaseDate);

            // 7. 生成唯一票券编码（使用专业服务）
            String ticketCode = ticketCodeGeneratorService.generateUniqueTicketCode(userId, purchaseDate);

            // 8. 生成订单编号
            String orderNo = generateOrderNo(userId, purchaseDate);

            TicketEntity ticketEntity = ticketEntityMapper.selectByDate(purchaseDate);

            // 9. 创建ticket_order订单
            TicketOrder ticketOrder = new TicketOrder();
            ticketOrder.setOrderNo(orderNo);
            ticketOrder.setUserId(userId);
            ticketOrder.setTicketId(ticketEntity.getId());
            ticketOrder.setTicketCode(ticketCode);
            ticketOrder.setTicketDate(purchaseDate);
            ticketOrder.setStatus(1); // 待支付
            ticketOrder.setAmount(0L); // 免费票券，金额为0
            ticketOrder.setCreateTime(new Date());
            ticketOrder.setUpdateTime(new Date());
            ticketOrder.setRemark("乐观锁购票生成");

            int insertResult = ticketOrderMapper.insert(ticketOrder);
            if (insertResult <= 0) {
                throw new RuntimeException("订单创建失败");
            }

            LOGGER.info("订单创建成功，订单号: {}, 用户ID: {}, 票券编码: {}",
                    orderNo, userId, ticketCode);

            // 添加购买记录到缓存
            PurchaseRecord purchaseRecord = new PurchaseRecord(userId, LocalDate.parse(purchaseDate), ticketCode);
            ticketCacheManager.addPurchaseRecord(userId, purchaseDate, purchaseRecord);
            
            // 手动确认消息
            channel.basicAck(deliveryTag, false);
            LOGGER.info("订单创建任务处理完成，用户ID: {}, 日期: {}, 订单号: {}", 
                    userId, purchaseDate, orderNo);
            
        } catch (Exception e) {
            LOGGER.error("处理订单创建消息失败: {}", e.getMessage(), e);
            try {
                // 消息重新入队以便重试
                channel.basicNack(deliveryTag, false, true);
            } catch (IOException ioException) {
                LOGGER.error("确认消息失败失败，错误: {}", ioException.getMessage(), ioException);
            }
        }
    }
    
    /**
     * 生成订单编号
     * 格式：TB + 时间戳 + 用户ID后4位 + 随机数
     *
     * @param userId 用户ID
     * @param date   购票日期
     * @return 订单编号
     */
    private String generateOrderNo(Long userId, String date) {
        long timestamp = System.currentTimeMillis();
        String userIdSuffix = String.valueOf(userId).substring(Math.max(0, String.valueOf(userId).length() - 4));
        int random = (int) (Math.random() * 1000);
        
        return String.format("TB%d%s%03d", timestamp, userIdSuffix, random);
    }
}