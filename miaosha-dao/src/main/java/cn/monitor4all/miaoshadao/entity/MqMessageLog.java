package cn.monitor4all.miaoshadao.entity;

import java.util.Date;

/**
 * MQ消息本地日志实体类
 */
public class MqMessageLog {

    /** 主键ID */
    private Long id;

    /** 消息唯一ID */
    private String messageId;

    /** 队列/交换机 */
    private String topic;

    /** 路由键 */
    private String routingKey;

    /** 消息JSON内容 */
    private String messageBody;

    /** 状态：0待发送 1已发送 2已确认 3已消费 -1发送失败 */
    private Integer status;

    /** 重试次数 */
    private Integer retryCount;

    /** 最大重试次数 */
    private Integer maxRetry;

    /** 下次重试时间 */
    private Date nextRetryTime;

    /** 创建时间 */
    private Date createTime;

    /** 更新时间 */
    private Date updateTime;

    public MqMessageLog() {}

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getRoutingKey() {
        return routingKey;
    }

    public void setRoutingKey(String routingKey) {
        this.routingKey = routingKey;
    }

    public String getMessageBody() {
        return messageBody;
    }

    public void setMessageBody(String messageBody) {
        this.messageBody = messageBody;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }

    public Integer getMaxRetry() {
        return maxRetry;
    }

    public void setMaxRetry(Integer maxRetry) {
        this.maxRetry = maxRetry;
    }

    public Date getNextRetryTime() {
        return nextRetryTime;
    }

    public void setNextRetryTime(Date nextRetryTime) {
        this.nextRetryTime = nextRetryTime;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

    @Override
    public String toString() {
        return "MqMessageLog{" +
                "id=" + id +
                ", messageId='" + messageId + '\'' +
                ", topic='" + topic + '\'' +
                ", routingKey='" + routingKey + '\'' +
                ", status=" + status +
                ", retryCount=" + retryCount +
                ", maxRetry=" + maxRetry +
                ", nextRetryTime=" + nextRetryTime +
                ", createTime=" + createTime +
                ", updateTime=" + updateTime +
                '}';
    }
}
