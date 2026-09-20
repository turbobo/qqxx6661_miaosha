package cn.monitor4all.miaoshadao.entity;

import java.util.Date;

/**
 * 访客预约本地事件。
 *
 * @author Qoder
 * @date 2026/09/20
 */
public class FulfillmentOutboxEvent {

    private String eventId;
    private String orderNo;
    private String eventType;
    private String payload;
    private String status;
    private Integer retryCount;
    private Date nextRetryTime;
    private Date createTime;
    private Date updateTime;

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }
    public Date getNextRetryTime() { return nextRetryTime; }
    public void setNextRetryTime(Date nextRetryTime) { this.nextRetryTime = nextRetryTime; }
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }
    public Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }
}
