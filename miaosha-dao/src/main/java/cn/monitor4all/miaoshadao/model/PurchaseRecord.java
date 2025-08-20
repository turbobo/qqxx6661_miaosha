package cn.monitor4all.miaoshadao.model;

import java.time.LocalDate;

public class PurchaseRecord {
    private Long userId;  // 改为Long类型
    private LocalDate date;
    private String ticketCode;
    
    public PurchaseRecord(Long userId, LocalDate date, String ticketCode) {  // 改为Long类型
        this.userId = userId;
        this.date = date;
        this.ticketCode = ticketCode;
    }
    
    // getter方法
    public Long getUserId() {  // 改为Long类型
        return userId;
    }
    
    public LocalDate getDate() {
        return date;
    }
    
    public String getTicketCode() {
        return ticketCode;
    }
}
