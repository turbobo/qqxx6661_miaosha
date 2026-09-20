package cn.monitor4all.miaoshaservice.job;

import cn.monitor4all.miaoshadao.entity.FulfillmentOutboxEvent;
import cn.monitor4all.miaoshadao.mapper.FulfillmentMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;

/**
 * 事务提交后的访客预约事件投递器。
 *
 * @author Qoder
 * @date 2026/09/20
 */
@Component
public class FulfillmentOutboxDispatcher {

    public static final String VISITOR_RESERVATION_QUEUE = "visitor.reservation.queue";
    private static final Logger LOGGER = LoggerFactory.getLogger(FulfillmentOutboxDispatcher.class);
    private static final int BATCH_SIZE = 100;

    @Resource
    private FulfillmentMapper fulfillmentMapper;

    @Resource
    private RabbitTemplate rabbitTemplate;

    /** 每三秒投递待处理或补偿中的访客预约事件。 */
    @Scheduled(fixedDelay = 3000)
    public void dispatchPendingEvents() {
        Date now = new Date();
        List<FulfillmentOutboxEvent> events = fulfillmentMapper.selectDispatchableEvents(now, BATCH_SIZE);
        for (FulfillmentOutboxEvent event : events) {
            if (fulfillmentMapper.markEventSent(event.getEventId(), now) != 1) {
                continue;
            }
            try {
                rabbitTemplate.convertAndSend(VISITOR_RESERVATION_QUEUE, event.getPayload());
                LOGGER.info("访客预约事件已投递，eventId={}, orderNo={}", event.getEventId(), event.getOrderNo());
            } catch (RuntimeException exception) {
                fulfillmentMapper.requeueEvent(event.getEventId(), now, now);
                LOGGER.error("访客预约事件投递失败，eventId={}", event.getEventId(), exception);
            }
        }
    }
}
