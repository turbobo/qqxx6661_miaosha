package cn.monitor4all.miaoshaweb.model;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 抢购消息实体类
 * 用于消息队列异步处理抢购请求
 */
public class PurchaseMessage implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
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
     * 验证码
     */
    private String verifyCode;
    
    /**
     * 请求时间
     */
    private LocalDateTime requestTime;
    
    /**
     * 消息状态：PENDING-待处理, PROCESSING-处理中, SUCCESS-成功, FAILED-失败
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
     * 重试次数
     */
    private int retryCount;
    
    /**
     * 最大重试次数
     */
    private int maxRetryCount;
    
    public PurchaseMessage() {
        this.requestTime = LocalDateTime.now();
        this.status = "PENDING";
        this.retryCount = 0;
        this.maxRetryCount = 3;
    }
    
    public PurchaseMessage(Long userId, String date, String verifyCode) {
        this();
        this.userId = userId;
        this.date = date;
        this.verifyCode = verifyCode;
        this.messageId = generateMessageId(userId, date);
    }
    
    /**
     * 生成消息ID
     */
    private String generateMessageId(Long userId, String date) {
        return "PURCHASE_" + userId + "_" + date.replace("-", "") + "_" + System.currentTimeMillis();
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
    
    public String getVerifyCode() {
        return verifyCode;
    }
    
    public void setVerifyCode(String verifyCode) {
        this.verifyCode = verifyCode;
    }
    
    public LocalDateTime getRequestTime() {
        return requestTime;
    }
    
    public void setRequestTime(LocalDateTime requestTime) {
        this.requestTime = requestTime;
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
    
    public int getRetryCount() {
        return retryCount;
    }
    
    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }
    
    public int getMaxRetryCount() {
        return maxRetryCount;
    }
    
    public void setMaxRetryCount(int maxRetryCount) {
        this.maxRetryCount = maxRetryCount;
    }
    
    /**
     * 增加重试次数
     */
    public void incrementRetryCount() {
        this.retryCount++;
    }
    
    /**
     * 检查是否还可以重试
     */
    public boolean canRetry() {
        return this.retryCount < this.maxRetryCount;
    }
    
    /**
     * 设置处理成功
     */
    public void setSuccess(String result) {
        this.status = "SUCCESS";
        this.result = result;
    }
    
    /**
     * 设置处理失败
     */
    public void setFailed(String errorMessage) {
        this.status = "FAILED";
        this.errorMessage = errorMessage;
    }
    
    /**
     * 设置处理中
     */
    public void setProcessing() {
        this.status = "PROCESSING";
    }
    
    @Override
    public String toString() {
        return "PurchaseMessage{" +
                "messageId='" + messageId + '\'' +
                ", userId=" + userId +
                ", date='" + date + '\'' +
                ", status='" + status + '\'' +
                ", result='" + result + '\'' +
                ", errorMessage='" + errorMessage + '\'' +
                ", retryCount=" + retryCount +
                '}';
    }
}
