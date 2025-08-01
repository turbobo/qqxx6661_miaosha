package cn.monitor4all.miaoshadao.model;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class Ticket {
    private LocalDate date;
    private String weekday;
    private int total;
    private int remaining;
    
    public Ticket(LocalDate date, int total) {
        this.date = date;
        this.total = total;
        this.remaining = total;
        
        // 获取星期几
        String[] weekdays = {"星期日", "星期一", "星期二", "星期三", "星期四", "星期五", "星期六"};
        this.weekday = weekdays[date.getDayOfWeek().getValue() % 7];
    }
    
    // getter和setter方法
    public LocalDate getDate() {
        return date;
    }
    
    public String getFormattedDate() {
        return date.format(DateTimeFormatter.ofPattern("yyyy.MM.dd"));
    }
    
    public String getWeekday() {
        return weekday;
    }
    
    public int getTotal() {
        return total;
    }
    
    public int getRemaining() {
        return remaining;
    }
    
    public void setRemaining(int remaining) {
        this.remaining = remaining;
    }
    
    public boolean purchase() {
        if (remaining > 0) {
            remaining--;
            return true;
        }
        return false;
    }
}
