package cn.monitor4all.miaoshaservice.service.impl;

import cn.monitor4all.miaoshadao.entity.MqMessageLog;
import cn.monitor4all.miaoshadao.mapper.MqMessageLogMapper;
import cn.monitor4all.miaoshaservice.service.CircuitBreakerService;
import cn.monitor4all.miaoshaservice.service.ReliableMessageService;
import com.alibaba.fastjson.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.UUID;

/**
 * 可靠消息发送服务实现
 * 基于本地消息表 + Publisher Confirm 实现消息可靠投递
 */
@Service
public class ReliableMessageServiceImpl implements ReliableMessageService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReliableMessageServiceImpl.class);

    /** 消息初始状态：待发送 */
    private static final int STATUS_PENDING = 0;

    /** 消息已发送状态 */
    private static final int STATUS_SENT = 1;

    /** 消息已确认状态 */
    private static final int STATUS_CONFIRMED = 2;

    /** 消息发送失败状态 */
    private static final int STATUS_FAILED = -1;

    /** 默认最大重试次数 */
    private static final int DEFAULT_MAX_RETRY = 5;

    /** 初始重试延迟（毫秒）：10秒 */
    private static final long INITIAL_RETRY_DELAY_MS = 10_000L;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private MqMessageLogMapper mqMessageLogMapper;

    @Autowired
    private CircuitBreakerService circuitBreakerService;

    /**
     * 发送可靠消息
     * 1. 生成UUID作为messageId
     * 2. 构建MqMessageLog对象并插入数据库
     * 3. 通过RabbitTemplate发送消息（受 MQ 熔断器保护），设置correlationData
     * 4. 更新状态为已发送；熔断时保留 status=0，由补偿任务重试
     *
     * @param exchange   交换机名称
     * @param routingKey 路由键
     * @param messageBody 消息体对象
     * @return messageId
     */
    @Override
    public String sendReliableMessage(String exchange, String routingKey, Object messageBody) {
        String messageId = UUID.randomUUID().toString().replace("-", "");
        String bodyJson = JSON.toJSONString(messageBody);
        Date now = new Date();

        // 构建消息日志记录
        MqMessageLog log = new MqMessageLog();
        log.setMessageId(messageId);
        log.setTopic(exchange);
        log.setRoutingKey(routingKey != null ? routingKey : "");
        log.setMessageBody(bodyJson);
        log.setStatus(STATUS_PENDING);
        log.setRetryCount(0);
        log.setMaxRetry(DEFAULT_MAX_RETRY);
        log.setNextRetryTime(new Date(now.getTime() + INITIAL_RETRY_DELAY_MS));
        log.setCreateTime(now);
        log.setUpdateTime(now);

        // 先写入本地消息表
        mqMessageLogMapper.insert(log);
        LOGGER.info("可靠消息已写入本地表, messageId={}, exchange={}, routingKey={}", messageId, exchange, routingKey);

        // 发送 MQ 消息（受熔断器保护）
        // 熔断打开时降级：保留 status=PENDING，由补偿任务稍后重试
        circuitBreakerService.executeWithMqBreaker(
                () -> {
                    CorrelationData correlationData = new CorrelationData(messageId);
                    rabbitTemplate.convertAndSend(exchange, routingKey, bodyJson, correlationData);
                    mqMessageLogMapper.updateStatus(messageId, STATUS_SENT);
                    LOGGER.info("可靠消息已发送至MQ, messageId={}", messageId);
                    return null;
                },
                () -> {
                    LOGGER.warn("MQ 熔断降级：消息保留本地表等待补偿任务重试, messageId={}", messageId);
                    return null;
                }
        );

        return messageId;
    }

    /**
     * Publisher Confirm 回调处理
     *
     * @param messageId 消息唯一ID
     * @param ack       broker是否确认
     */
    @Override
    public void confirmCallback(String messageId, boolean ack) {
        if (messageId == null) {
            LOGGER.warn("confirmCallback收到null messageId");
            return;
        }
        if (ack) {
            mqMessageLogMapper.updateStatus(messageId, STATUS_CONFIRMED);
            LOGGER.info("消息已确认, messageId={}", messageId);
        } else {
            mqMessageLogMapper.updateStatus(messageId, STATUS_FAILED);
            LOGGER.error("消息确认失败(nack), messageId={}, 将由补偿任务重试", messageId);
        }
    }

    /**
     * 标记消息为已消费
     *
     * @param messageId 消息唯一ID
     */
    @Override
    public void markConsumed(String messageId) {
        if (messageId == null) {
            LOGGER.warn("markConsumed收到null messageId");
            return;
        }
        mqMessageLogMapper.markAsConsumed(messageId);
        LOGGER.info("消息已标记为已消费, messageId={}", messageId);
    }
}
