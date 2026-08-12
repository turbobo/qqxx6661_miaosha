package cn.monitor4all.miaoshadao.model;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class PurchaseRequest {
    private Long userId;
    private String date; // 改为String类型，支持前端传递的日期字符串

    // 添加verifyHash属性
    private String verifyHash;

    /**
     * 预约场次，当前库存仍按date维度扣减，字段用于最终移动端方案的场次展示和订单备注。
     */
    private String sessionId;

    private String sessionName;

    private String visitorName;

    private String visitorPhone;

    /**
     * 幂等请求ID，由前端生成的UUID。
     * 用于防止同一请求被重复提交和处理。
     */
    private String requestId;
    
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

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getSessionName() {
        return sessionName;
    }

    public void setSessionName(String sessionName) {
        this.sessionName = sessionName;
    }

    public String getVisitorName() {
        return visitorName;
    }

    public void setVisitorName(String visitorName) {
        this.visitorName = visitorName;
    }

    public String getVisitorPhone() {
        return visitorPhone;
    }

    public void setVisitorPhone(String visitorPhone) {
        this.visitorPhone = visitorPhone;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
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
