package cn.monitor4all.miaoshadao.model;

import java.util.Date;

public class CancelPurchaseResponse {
    private String orderNo;        // 订单编号
    private String ticketCode;     // 票券编码
    private String cancelTime;     // 取消时间
    private String cancelReason;   // 取消原因
    private Integer refundAmount;  // 退款金额
    private String status;         // 订单状态
    
    // 构造函数
    public CancelPurchaseResponse() {}
    
    public CancelPurchaseResponse(String orderNo, String ticketCode, String cancelTime, 
                                String cancelReason, Integer refundAmount, String status) {
        this.orderNo = orderNo;
        this.ticketCode = ticketCode;
        this.cancelTime = cancelTime;
        this.cancelReason = cancelReason;
        this.refundAmount = refundAmount;
        this.status = status;
    }
    
    // getter和setter方法
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
    
    public String getCancelTime() {
        return cancelTime;
    }
    
    public void setCancelTime(String cancelTime) {
        this.cancelTime = cancelTime;
    }
    
    public String getCancelReason() {
        return cancelReason;
    }
    
    public void setCancelReason(String cancelReason) {
        this.cancelReason = cancelReason;
    }
    
    public Integer getRefundAmount() {
        return refundAmount;
    }
    
    public void setRefundAmount(Integer refundAmount) {
        this.refundAmount = refundAmount;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    @Override
    public String toString() {
        return "CancelPurchaseResponse{" +
                "orderNo='" + orderNo + '\'' +
                ", ticketCode='" + ticketCode + '\'' +
                ", cancelTime='" + cancelTime + '\'' +
                ", cancelReason='" + cancelReason + '\'' +
                ", refundAmount=" + refundAmount +
                ", status='" + status + '\'' +
                '}';
    }
}
