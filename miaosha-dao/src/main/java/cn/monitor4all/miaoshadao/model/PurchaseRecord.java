package cn.monitor4all.miaoshadao.model;

import java.time.LocalDate;

public class PurchaseRecord {
    private String userId;
    private LocalDate date;
    private String ticketCode;
    
    public PurchaseRecord(String userId, LocalDate date, String ticketCode) {
        this.userId = userId;
        this.date = date;
        this.ticketCode = ticketCode;
    }
    
    // getter方法
    public String getUserId() {
        return userId;
    }
    
    public LocalDate getDate() {
        return date;
    }
    
    public String getTicketCode() {
        return ticketCode;
    }
}
