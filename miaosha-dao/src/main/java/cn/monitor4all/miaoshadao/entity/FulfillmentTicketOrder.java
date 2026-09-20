package cn.monitor4all.miaoshadao.entity;

import java.util.Date;

/**
 * 购票权益订单。
 * 购票成功即生效，访客预约失败只能进入补偿或人工兜底，不可取消该权益。
 *
 * @author Qoder
 * @date 2026/09/20
 */
public class FulfillmentTicketOrder {

    private String orderNo;
    private Long userId;
    private Integer stockId;
    private String visitSlot;
    private String ticketStatus;
    private String fulfillmentStatus;
    private Date createTime;
    private Date updateTime;

    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Integer getStockId() { return stockId; }
    public void setStockId(Integer stockId) { this.stockId = stockId; }
    public String getVisitSlot() { return visitSlot; }
    public void setVisitSlot(String visitSlot) { this.visitSlot = visitSlot; }
    public String getTicketStatus() { return ticketStatus; }
    public void setTicketStatus(String ticketStatus) { this.ticketStatus = ticketStatus; }
    public String getFulfillmentStatus() { return fulfillmentStatus; }
    public void setFulfillmentStatus(String fulfillmentStatus) { this.fulfillmentStatus = fulfillmentStatus; }
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }
    public Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }
}
