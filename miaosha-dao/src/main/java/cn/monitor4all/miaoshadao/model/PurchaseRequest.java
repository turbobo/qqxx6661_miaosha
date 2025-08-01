package cn.monitor4all.miaoshadao.model;

import java.time.LocalDate;

public class PurchaseRequest {
    private String userId;
    private LocalDate date;
    
    // getter和setter方法
    public String getUserId() {
        return userId;
    }
    
    public void setUserId(String userId) {
        this.userId = userId;
    }
    
    public LocalDate getDate() {
        return date;
    }
    
    public void setDate(LocalDate date) {
        this.date = date;
    }
}
