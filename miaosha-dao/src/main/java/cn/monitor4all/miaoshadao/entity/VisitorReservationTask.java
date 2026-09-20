package cn.monitor4all.miaoshadao.entity;

import java.util.Date;

/**
 * 访客预约履约任务。
 *
 * @author Qoder
 * @date 2026/09/20
 */
public class VisitorReservationTask {

    private String orderNo;
    private String reservationNo;
    private Long userId;
    private String visitSlot;
    private String status;
    private Integer retryCount;
    private Date nextRetryTime;
    private String lastErrorCode;
    private String lastErrorMessage;
    private String operator;
    private Date completeTime;
    private Date createTime;
    private Date updateTime;

    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public String getReservationNo() { return reservationNo; }
    public void setReservationNo(String reservationNo) { this.reservationNo = reservationNo; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getVisitSlot() { return visitSlot; }
    public void setVisitSlot(String visitSlot) { this.visitSlot = visitSlot; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }
    public Date getNextRetryTime() { return nextRetryTime; }
    public void setNextRetryTime(Date nextRetryTime) { this.nextRetryTime = nextRetryTime; }
    public String getLastErrorCode() { return lastErrorCode; }
    public void setLastErrorCode(String lastErrorCode) { this.lastErrorCode = lastErrorCode; }
    public String getLastErrorMessage() { return lastErrorMessage; }
    public void setLastErrorMessage(String lastErrorMessage) { this.lastErrorMessage = lastErrorMessage; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public Date getCompleteTime() { return completeTime; }
    public void setCompleteTime(Date completeTime) { this.completeTime = completeTime; }
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }
    public Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }
}
