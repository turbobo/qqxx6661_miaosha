package cn.monitor4all.miaoshaservice.service;

/**
 * 秒杀活动状态管理服务
 * 用于控制秒杀活动的开启和暂停状态
 */
public interface MiaoshaStatusService {
    
    /**
     * 暂停秒杀活动
     * @return 是否成功暂停
     */
    boolean pauseMiaosha();
    
    /**
     * 恢复秒杀活动
     * @return 是否成功恢复
     */
    boolean resumeMiaosha();
    
    /**
     * 检查秒杀活动是否暂停
     * @return true表示暂停，false表示正常
     */
    boolean isMiaoshaPaused();
    
    /**
     * 获取秒杀活动状态
     * @return 状态描述
     */
    String getMiaoshaStatus();
    
    /**
     * 获取秒杀活动状态详情
     * @return 状态详情对象
     */
    MiaoshaStatus getMiaoshaStatusDetail();
    
    /**
     * 秒杀活动状态
     */
    class MiaoshaStatus {
        private boolean paused;
        private String status;
        private long pauseTime;
        private long resumeTime;
        private String operator;
        private String reason;
        
        public MiaoshaStatus() {}
        
        public MiaoshaStatus(boolean paused, String status) {
            this.paused = paused;
            this.status = status;
        }
        
        // Getters and Setters
        public boolean isPaused() { return paused; }
        public void setPaused(boolean paused) { this.paused = paused; }
        
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        
        public long getPauseTime() { return pauseTime; }
        public void setPauseTime(long pauseTime) { this.pauseTime = pauseTime; }
        
        public long getResumeTime() { return resumeTime; }
        public void setResumeTime(long resumeTime) { this.resumeTime = resumeTime; }
        
        public String getOperator() { return operator; }
        public void setOperator(String operator) { this.operator = operator; }
        
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }
}
