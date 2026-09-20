package cn.monitor4all.miaoshaservice.model;

/**
 * 面向页面的购票与访客预约状态。
 *
 * @author Qoder
 * @date 2026/09/20
 */
public class FulfillmentStatusView {

    private String orderNo;
    private Long userId;
    private String visitSlot;
    private String ticketStatus;
    private String fulfillmentStatus;
    private Integer retryCount;
    private String lastErrorMessage;
    private String nextRetryTime;

    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getVisitSlot() { return visitSlot; }
    public void setVisitSlot(String visitSlot) { this.visitSlot = visitSlot; }
    public String getTicketStatus() { return ticketStatus; }
    public void setTicketStatus(String ticketStatus) { this.ticketStatus = ticketStatus; }
    public String getFulfillmentStatus() { return fulfillmentStatus; }
    public void setFulfillmentStatus(String fulfillmentStatus) { this.fulfillmentStatus = fulfillmentStatus; }
    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }
    public String getLastErrorMessage() { return lastErrorMessage; }
    public void setLastErrorMessage(String lastErrorMessage) { this.lastErrorMessage = lastErrorMessage; }
    public String getNextRetryTime() { return nextRetryTime; }
    public void setNextRetryTime(String nextRetryTime) { this.nextRetryTime = nextRetryTime; }
}
