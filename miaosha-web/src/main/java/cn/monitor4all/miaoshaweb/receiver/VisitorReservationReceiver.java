package cn.monitor4all.miaoshaweb.receiver;

import cn.monitor4all.miaoshaservice.model.VisitorReservationEvent;
import cn.monitor4all.miaoshaservice.service.VisitorFulfillmentService;
import com.alibaba.fastjson.JSON;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 访客预约事件消费者，处理成功后才确认消息。
 *
 * @author Qoder
 * @date 2026/09/20
 */
@Component
@RabbitListener(queues = "visitor.reservation.queue")
public class VisitorReservationReceiver {

    private static final Logger LOGGER = LoggerFactory.getLogger(VisitorReservationReceiver.class);

    @Resource
    private VisitorFulfillmentService visitorFulfillmentService;

    @RabbitHandler
    public void process(String body, @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag, Channel channel) {
        try {
            VisitorReservationEvent event = JSON.parseObject(body, VisitorReservationEvent.class);
            visitorFulfillmentService.processVisitorReservation(event);
            channel.basicAck(deliveryTag, false);
        } catch (Exception exception) {
            LOGGER.error("访客预约事件处理异常，消息将重新入队", exception);
            try {
                channel.basicNack(deliveryTag, false, true);
            } catch (Exception acknowledgeException) {
                LOGGER.error("访客预约消息确认失败", acknowledgeException);
            }
        }
    }
}
