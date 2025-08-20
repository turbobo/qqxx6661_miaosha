package cn.monitor4all.miaoshaweb.model;

import java.time.LocalDateTime;

/**
 * 抢购结果查询响应类
 */
public class PurchaseResult {
    
    /**
     * 消息ID
     */
    private String messageId;
    
    /**
     * 用户ID
     */
    private Long userId;
    
    /**
     * 票券日期
     */
    private String date;
    
    /**
     * 处理状态：PENDING-待处理, PROCESSING-处理中, SUCCESS-成功, FAILED-失败
     */
    private String status;
    
    /**
     * 处理结果
     */
    private String result;
    
    /**
     * 错误信息
     */
    private String errorMessage;
    
    /**
     * 票券编码（成功时返回）
     */
    private String ticketCode;
    
    /**
     * 订单编号（成功时返回）
     */
    private String orderNo;
    
    /**
     * 请求时间
     */
    private LocalDateTime requestTime;
    
    /**
     * 处理完成时间
     */
    private LocalDateTime completedTime;
    
    /**
     * 是否已存在订单
     */
    private boolean orderExists;
    
    /**
     * 订单信息（如果已存在）
     */
    private Object existingOrder;
    
    public PurchaseResult() {
    }
    
    public PurchaseResult(String messageId, Long userId, String date, String status) {
        this.messageId = messageId;
        this.userId = userId;
        this.date = date;
        this.status = status;
        this.requestTime = LocalDateTime.now();
    }
    
    // Getter和Setter方法
    public String getMessageId() {
        return messageId;
    }
    
    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }
    
    public Long getUserId() {
        return userId;
    }
    
    public void setUserId(Long userId) {
        this.userId = userId;
    }
    
    public String getDate() {
        return date;
    }
    
    public void setDate(String date) {
        this.date = date;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public String getResult() {
        return result;
    }
    
    public void setResult(String result) {
        this.result = result;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
    
    public String getTicketCode() {
        return ticketCode;
    }
    
    public void setTicketCode(String ticketCode) {
        this.ticketCode = ticketCode;
    }
    
    public String getOrderNo() {
        return orderNo;
    }
    
    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }
    
    public LocalDateTime getRequestTime() {
        return requestTime;
    }
    
    public void setRequestTime(LocalDateTime requestTime) {
        this.requestTime = requestTime;
    }
    
    public LocalDateTime getCompletedTime() {
        return completedTime;
    }
    
    public void setCompletedTime(LocalDateTime completedTime) {
        this.completedTime = completedTime;
    }
    
    public boolean isOrderExists() {
        return orderExists;
    }
    
    public void setOrderExists(boolean orderExists) {
        this.orderExists = orderExists;
    }
    
    public Object getExistingOrder() {
        return existingOrder;
    }
    
    public void setExistingOrder(Object existingOrder) {
        this.existingOrder = existingOrder;
    }
    
    /**
     * 检查是否处理完成
     */
    public boolean isCompleted() {
        return "SUCCESS".equals(status) || "FAILED".equals(status);
    }
    
    /**
     * 检查是否成功
     */
    public boolean isSuccess() {
        return "SUCCESS".equals(status);
    }
    
    /**
     * 检查是否失败
     */
    public boolean isFailed() {
        return "FAILED".equals(status);
    }
    
    /**
     * 检查是否正在处理
     */
    public boolean isProcessing() {
        return "PROCESSING".equals(status);
    }
    
    /**
     * 检查是否等待处理
     */
    public boolean isPending() {
        return "PENDING".equals(status);
    }
    
    @Override
    public String toString() {
        return "PurchaseResult{" +
                "messageId='" + messageId + '\'' +
                ", userId=" + userId +
                ", date='" + date + '\'' +
                ", status='" + status + '\'' +
                ", result='" + result + '\'' +
                ", ticketCode='" + ticketCode + '\'' +
                ", orderNo='" + orderNo + '\'' +
                ", orderExists=" + orderExists +
                '}';
    }
}
