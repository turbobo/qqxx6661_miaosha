package cn.monitor4all.miaoshaservice.service;

/**
 * 可靠消息发送服务接口
 * 基于本地消息表 + Publisher Confirm 实现消息可靠投递
 */
public interface ReliableMessageService {

    /**
     * 发送可靠消息
     * 先写入本地消息表，再异步发送MQ消息
     *
     * @param exchange   交换机名称
     * @param routingKey 路由键
     * @param messageBody 消息体对象（将被JSON序列化）
     * @return messageId 消息唯一ID
     */
    String sendReliableMessage(String exchange, String routingKey, Object messageBody);

    /**
     * Publisher Confirm 回调处理
     *
     * @param messageId 消息唯一ID
     * @param ack       是否确认
     */
    void confirmCallback(String messageId, boolean ack);

    /**
     * 标记消息为已消费
     *
     * @param messageId 消息唯一ID
     */
    void markConsumed(String messageId);
}
