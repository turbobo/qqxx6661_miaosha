package cn.monitor4all.miaoshadao.model;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class CancelPurchaseRequest {
    private Long userId;           // 用户ID
    private String orderNo;        // 订单编号（可选）
    private String ticketCode;     // 票券编码（可选）
    private String date;           // 票券日期
    private String cancelReason;   // 取消原因
    private String verifyHash;     // 验证哈希（防重放）
    
    // 构造函数
    public CancelPurchaseRequest() {}
    
    public CancelPurchaseRequest(Long userId, String orderNo, String ticketCode, String date, String cancelReason, String verifyHash) {
        this.userId = userId;
        this.orderNo = orderNo;
        this.ticketCode = ticketCode;
        this.date = date;
        this.cancelReason = cancelReason;
        this.verifyHash = verifyHash;
    }
    
    // getter和setter方法
    public Long getUserId() {
        return userId;
    }
    
    public void setUserId(Long userId) {
        this.userId = userId;
    }
    
    public String getOrderNo() {
        return orderNo;
    }
    
    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }
    
    public String getTicketCode() {
        return ticketCode;
    }
    
    public void setTicketCode(String ticketCode) {
        this.ticketCode = ticketCode;
    }
    
    public String getDate() {
        return date;
    }
    
    public void setDate(String date) {
        this.date = date;
    }
    
    public String getCancelReason() {
        return cancelReason;
    }
    
    public void setCancelReason(String cancelReason) {
        this.cancelReason = cancelReason;
    }
    
    public String getVerifyHash() {
        return verifyHash;
    }
    
    public void setVerifyHash(String verifyHash) {
        this.verifyHash = verifyHash;
    }
    
    /**
     * 获取LocalDate对象
     * @return LocalDate对象，如果date为null或格式错误则返回null
     */
    public LocalDate getLocalDate() {
        if (date == null || date.isEmpty()) {
            return null;
        }
        try {
            // 支持多种日期格式
            if (date.contains(".")) {
                // 格式: 2024.01.15
                return LocalDate.parse(date, DateTimeFormatter.ofPattern("yyyy.MM.dd"));
            } else if (date.contains("-")) {
                // 格式: 2024-01-15
                return LocalDate.parse(date, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            } else {
                // 格式: 20240115
                return LocalDate.parse(date, DateTimeFormatter.ofPattern("yyyyMMdd"));
            }
        } catch (Exception e) {
            return null;
        }
    }
    
    @Override
    public String toString() {
        return "CancelPurchaseRequest{" +
                "userId=" + userId +
                ", orderNo='" + orderNo + '\'' +
                ", ticketCode='" + ticketCode + '\'' +
                ", date='" + date + '\'' +
                ", cancelReason='" + cancelReason + '\'' +
                ", verifyHash='" + verifyHash + '\'' +
                '}';
    }
}
