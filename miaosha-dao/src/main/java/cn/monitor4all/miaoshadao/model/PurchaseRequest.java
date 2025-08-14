package cn.monitor4all.miaoshadao.model;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class PurchaseRequest {
    private Long userId;
    private String date; // 改为String类型，支持前端传递的日期字符串

    // 添加verifyHash属性
    private String verifyHash;
    
    // getter和setter方法
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
}
