package cn.monitor4all.miaoshadao.model;

/**
 * 秒杀状态响应实体类
 * 用于获取秒杀活动状态的响应
 */
public class MiaoshaStatusResponse {
    private boolean paused;
    private String status;
    private long pauseTime;
    private long resumeTime;
    private String operator;
    private String reason;
    private long timestamp;
    
    public MiaoshaStatusResponse() {}
    
    public MiaoshaStatusResponse(boolean paused, String status) {
        this.paused = paused;
        this.status = status;
        this.timestamp = System.currentTimeMillis();
    }
    
    // Getters and Setters
    public boolean isPaused() {
        return paused;
    }
    
    public void setPaused(boolean paused) {
        this.paused = paused;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public long getPauseTime() {
        return pauseTime;
    }
    
    public void setPauseTime(long pauseTime) {
        this.pauseTime = pauseTime;
    }
    
    public long getResumeTime() {
        return resumeTime;
    }
    
    public void setResumeTime(long resumeTime) {
        this.resumeTime = resumeTime;
    }
    
    public String getOperator() {
        return operator;
    }
    
    public void setOperator(String operator) {
        this.operator = operator;
    }
    
    public String getReason() {
        return reason;
    }
    
    public void setReason(String reason) {
        this.reason = reason;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
