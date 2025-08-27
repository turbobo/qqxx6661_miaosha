package cn.monitor4all.miaoshadao.model;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class Ticket {
    private String date;
    private String weekday;
    private int total;
    private int remaining;
    
    // 添加用户是否已购买的标识
    private boolean userPurchased;
    
    // 添加无参构造函数以支持Jackson反序列化
    public Ticket() {
    }
    
    public Ticket(String date, int total) {
        this.date = date;
        this.total = total;
        this.remaining = total;
        this.userPurchased = false; // 默认未购买
        
        // 获取星期几
        try {
            LocalDate localDate = LocalDate.parse(date, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            String[] weekdays = {"星期日", "星期一", "星期二", "星期三", "星期四", "星期五", "星期六"};
            this.weekday = weekdays[localDate.getDayOfWeek().getValue() % 7];
        } catch (Exception e) {
            this.weekday = "未知";
        }
    }
    
    // getter和setter方法
    public String getDate() {
        return date;
    }
    
    public void setDate(String date) {
        this.date = date;
    }
    
    public String getFormattedDate() {
        return date != null ? date : "";
    }
    
    public String getWeekday() {
        return weekday;
    }
    
    public void setWeekday(String weekday) {
        this.weekday = weekday;
    }
    
    public int getTotal() {
        return total;
    }
    
    public void setTotal(int total) {
        this.total = total;
    }
    
    public int getRemaining() {
        return remaining;
    }
    
    public void setRemaining(int remaining) {
        this.remaining = remaining;
    }
    
    // 新增字段的getter和setter
    public boolean isUserPurchased() {
        return userPurchased;
    }
    
    public void setUserPurchased(boolean userPurchased) {
        this.userPurchased = userPurchased;
    }
    
    public boolean purchase() {
        if (remaining > 0) {
            remaining--;
            return true;
        }
        return false;
    }
    
    /**
     * 获取剩余票数百分比
     * @return 百分比值 (0-100)
     */
    public double getRemainingPercentage() {
        if (total == 0) return 0;
        return (double) remaining / total * 100;
    }
    
    /**
     * 检查是否售罄
     * @return 是否售罄
     */
    public boolean isSoldOut() {
        return remaining <= 0;
    }
    
    /**
     * 检查库存是否紧张（剩余少于20%）
     * @return 是否库存紧张
     */
    public boolean isLowStock() {
        return getRemainingPercentage() < 20;
    }
}