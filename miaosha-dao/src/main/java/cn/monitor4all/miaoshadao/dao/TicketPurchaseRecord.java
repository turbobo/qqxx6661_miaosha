package cn.monitor4all.miaoshadao.dao;

import java.util.Date;

/**
 * 票券购买记录数据库实体类
 */
public class TicketPurchaseRecord {
    private Integer id;
    private String orderId;
    private Long userId;  // 改为Long类型
    private Integer ticketId;
    private String ticketDate;  // 改为String类型
    private String ticketCode;
    private Date purchaseTime;
    private Integer status;
    private Date expireTime;
    private Date createTime;
    private Date updateTime;
    
    // 构造函数
    public TicketPurchaseRecord() {}
    
    public TicketPurchaseRecord(Long userId, String ticketDate, String ticketCode) {  // 改为Long类型
        this.userId = userId;
        this.ticketDate = ticketDate;
        this.ticketCode = ticketCode;
        this.status = 1;
        this.purchaseTime = new Date();
        // 设置过期时间为当天23:59:59
        this.expireTime = new Date();
    }
    
    // getter和setter方法
    public Integer getId() {
        return id;
    }
    
    public void setId(Integer id) {
        this.id = id;
    }
    
    public String getOrderId() {
        return orderId;
    }
    
    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }
    
    public Long getUserId() {  // 改为Long类型
        return userId;
    }
    
    public void setUserId(Long userId) {  // 改为Long类型
        this.userId = userId;
    }
    
    public Integer getTicketId() {
        return ticketId;
    }
    
    public void setTicketId(Integer ticketId) {
        this.ticketId = ticketId;
    }
    
    public String getTicketDate() {  // 改为String类型
        return ticketDate;
    }
    
    public void setTicketDate(String ticketDate) {  // 改为String类型
        this.ticketDate = ticketDate;
    }
    
    public String getTicketCode() {
        return ticketCode;
    }
    
    public void setTicketCode(String ticketCode) {
        this.ticketCode = ticketCode;
    }
    
    public Date getPurchaseTime() {
        return purchaseTime;
    }
    
    public void setPurchaseTime(Date purchaseTime) {
        this.purchaseTime = purchaseTime;
    }
    
    public Integer getStatus() {
        return status;
    }
    
    public void setStatus(Integer status) {
        this.status = status;
    }
    
    public Date getExpireTime() {
        return expireTime;
    }
    
    public void setExpireTime(Date expireTime) {
        this.expireTime = expireTime;
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
        return "TicketPurchaseRecord{" +
                "id=" + id +
                ", orderId='" + orderId + '\'' +
                ", userId=" + userId +  // 改为Long类型，不需要引号
                ", ticketId=" + ticketId +
                ", ticketDate='" + ticketDate + '\'' +
                ", ticketCode='" + ticketCode + '\'' +
                ", purchaseTime=" + purchaseTime +
                ", status=" + status +
                ", expireTime=" + expireTime +
                ", createTime=" + createTime +
                ", updateTime=" + updateTime +
                '}';
    }
}