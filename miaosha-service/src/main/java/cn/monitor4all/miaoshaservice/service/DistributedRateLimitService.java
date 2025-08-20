package cn.monitor4all.miaoshaservice.service;

/**
 * 分布式限流服务接口
 * 基于Redis+Lua脚本实现分布式环境下的接口限流
 */
public interface DistributedRateLimitService {
    
    /**
     * 尝试获取令牌（非阻塞）
     * @param key 限流键（通常是接口路径或用户ID）
     * @param limit 限流次数
     * @param window 时间窗口（秒）
     * @return 是否获取成功
     */
    boolean tryAcquire(String key, int limit, int window);
    
    /**
     * 尝试获取令牌（阻塞等待）
     * @param key 限流键
     * @param limit 限流次数
     * @param window 时间窗口（秒）
     * @param timeout 等待超时时间（毫秒）
     * @return 是否获取成功
     */
    boolean tryAcquire(String key, int limit, int window, long timeout);
    
    /**
     * 获取当前令牌数量
     * @param key 限流键
     * @return 剩余令牌数量
     */
    int getCurrentTokens(String key);
    
    /**
     * 重置限流计数器
     * @param key 限流键
     * @return 是否重置成功
     */
    boolean resetRateLimit(String key);
    
    /**
     * 检查是否被限流
     * @param key 限流键
     * @param limit 限流次数
     * @param window 时间窗口（秒）
     * @return 是否被限流
     */
    boolean isRateLimited(String key, int limit, int window);
    
    /**
     * 获取限流信息
     * @param key 限流键
     * @return 限流信息（包含剩余令牌数、重置时间等）
     */
    RateLimitInfo getRateLimitInfo(String key);
    
    /**
     * 限流信息类
     */
    class RateLimitInfo {
        private String key;
        private int remainingTokens;
        private long resetTime;
        private int limit;
        private int window;
        
        public RateLimitInfo(String key, int remainingTokens, long resetTime, int limit, int window) {
            this.key = key;
            this.remainingTokens = remainingTokens;
            this.resetTime = resetTime;
            this.limit = limit;
            this.window = window;
        }
        
        // Getters
        public String getKey() { return key; }
        public int getRemainingTokens() { return remainingTokens; }
        public long getResetTime() { return resetTime; }
        public int getLimit() { return limit; }
        public int getWindow() { return window; }
        
        @Override
        public String toString() {
            return String.format("RateLimitInfo{key='%s', remainingTokens=%d, resetTime=%d, limit=%d, window=%d}", 
                key, remainingTokens, resetTime, limit, window);
        }
    }
}
