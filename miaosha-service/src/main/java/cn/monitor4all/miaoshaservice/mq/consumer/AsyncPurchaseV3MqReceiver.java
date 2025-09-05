package cn.monitor4all.miaoshaservice.mq.consumer;

import cn.monitor4all.miaoshadao.model.PurchaseRequest;
import cn.monitor4all.miaoshaservice.service.TicketService;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@RabbitListener(queues = "asyncPurchaseQueueV3")
public class AsyncPurchaseV3MqReceiver {

    private static final Logger LOGGER = LoggerFactory.getLogger(AsyncPurchaseV3MqReceiver.class);

    @Resource
    private TicketService ticketService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    // 添加消息计数器，用于跟踪接收的消息数量
    private static final AtomicInteger messageCounter = new AtomicInteger(0);

    @RabbitHandler
    public void process(String message) {
        LOGGER.info("AsyncPurchaseMqReceiver收到消息开始用户下单流程: " + message);
        int count = messageCounter.incrementAndGet();

        JSONObject jsonObject = JSONObject.parseObject(message);
        try {
            LOGGER.info("收到异步抢购消息，请求: {}, 线程: {}, 消息计数: {}",
                    JSON.toJSONString(message), Thread.currentThread().getName(), count);

            // 提取消息内容
            String requestId = (String) jsonObject.get("requestId");
            Long userId = Long.valueOf(jsonObject.get("userId").toString());
            String date = (String) jsonObject.get("date");
            String verifyHash = (String) jsonObject.get("verifyHash");
            Long timestamp = (Long) jsonObject.get("timestamp");

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
        } catch (Exception e) {
            LOGGER.error("AsyncPurchaseV3MqReceiver处理异步抢购消息失败，消息计数: {}, 错误: {}", count, e.getMessage(), e);
        }
    }
}