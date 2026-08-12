package cn.monitor4all.miaoshaservice.job;

import cn.monitor4all.miaoshadao.entity.MqMessageLog;
import cn.monitor4all.miaoshadao.mapper.MqMessageLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

/**
 * MQ消息补偿定时任务
 * 定期扫描本地消息表中未确认/发送失败的消息，进行重新发送
 */
@Component
@EnableScheduling
public class MessageCompensationJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(MessageCompensationJob.class);

    /** 默认最大重试次数 */
    private static final int DEFAULT_MAX_RETRY = 5;

    /** 重试间隔基数（毫秒）：30秒 */
    private static final long RETRY_INTERVAL_BASE_MS = 30_000L;

    /** 消息发送失败状态 */
    private static final int STATUS_FAILED = -1;

    /** 消息已发送状态（重发后标记） */
    private static final int STATUS_SENT = 1;

    @Autowired
    private MqMessageLogMapper mqMessageLogMapper;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    /**
     * 每30秒执行一次消息补偿
     * 查询 pending/failed 且超时的消息，重新发送MQ
     */
    @Scheduled(fixedDelay = 30000)
    public void compensateMessages() {
        Date now = new Date();
        List<MqMessageLog> pendingMessages = mqMessageLogMapper.selectPendingMessages(now, DEFAULT_MAX_RETRY);

        if (pendingMessages == null || pendingMessages.isEmpty()) {
            return;
        }

        LOGGER.info("消息补偿任务扫描到{}条待重试消息", pendingMessages.size());

        for (MqMessageLog msgLog : pendingMessages) {
            try {
                compensateSingleMessage(msgLog, now);
            } catch (Exception e) {
                LOGGER.error("消息补偿失败, messageId={}, error={}", msgLog.getMessageId(), e.getMessage(), e);
            }
        }
    }

    /**
     * 补偿单条消息
     *
     * @param msgLog 消息日志
     * @param now    当前时间
     */
    private void compensateSingleMessage(MqMessageLog msgLog, Date now) {
        int newRetryCount = msgLog.getRetryCount() + 1;

        if (newRetryCount >= DEFAULT_MAX_RETRY) {
            LOGGER.error("消息超过最大重试次数({}), 请人工排查! messageId={}, topic={}, routingKey={}",
                    DEFAULT_MAX_RETRY, msgLog.getMessageId(), msgLog.getTopic(), msgLog.getRoutingKey());
            // 仍然更新重试次数，避免反复扫描
            mqMessageLogMapper.updateStatusAndRetry(msgLog.getMessageId(), STATUS_FAILED, newRetryCount, null);
            return;
        }

        // 递增间隔：30s * retryCount
        long delayMs = RETRY_INTERVAL_BASE_MS * newRetryCount;
        Date nextRetryTime = new Date(now.getTime() + delayMs);

        try {
            // 重新发送MQ消息
            CorrelationData correlationData = new CorrelationData(msgLog.getMessageId());
            rabbitTemplate.convertAndSend(msgLog.getTopic(), msgLog.getRoutingKey(), msgLog.getMessageBody(), correlationData);

            // 更新状态为已发送，增加重试次数
            mqMessageLogMapper.updateStatusAndRetry(msgLog.getMessageId(), STATUS_SENT, newRetryCount, nextRetryTime);
            LOGGER.info("消息补偿重发成功, messageId={}, retryCount={}", msgLog.getMessageId(), newRetryCount);
        } catch (Exception e) {
            // 发送失败，保持失败状态，更新重试次数和下次重试时间
            mqMessageLogMapper.updateStatusAndRetry(msgLog.getMessageId(), STATUS_FAILED, newRetryCount, nextRetryTime);
            LOGGER.error("消息补偿重发失败, messageId={}, retryCount={}, error={}",
                    msgLog.getMessageId(), newRetryCount, e.getMessage(), e);
        }
    }
}
