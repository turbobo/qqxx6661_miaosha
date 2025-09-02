package cn.monitor4all.miaoshadao.model;

/**
 * 秒杀操作响应实体类
 * 用于暂停/恢复秒杀活动的响应
 */
public class MiaoshaOperationResponse {
    private String status;
    private String message;
    private long timestamp;
    
    public MiaoshaOperationResponse() {}
    
    public MiaoshaOperationResponse(String status, String message) {
        this.status = status;
        this.message = message;
        this.timestamp = System.currentTimeMillis();
    }
    
    public MiaoshaOperationResponse(String status, String message, long timestamp) {
        this.status = status;
        this.message = message;
        this.timestamp = timestamp;
    }
    
    // Getters and Setters
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
