package cn.monitor4all.miaoshaservice.service;

/**
 * 分布式令牌桶限流服务接口
 * 基于Redis+Lua脚本实现分布式环境下的令牌桶限流
 */
public interface DistributedTokenBucketService {
    
    /**
     * 尝试获取令牌（非阻塞）
     * @param key 限流键（通常是接口路径或用户ID）
     * @param capacity 桶容量
     * @param rate 令牌填充速率（每秒）
     * @return 是否获取成功
     */
    boolean tryAcquire(String key, int capacity, double rate);
    
    /**
     * 尝试获取指定数量的令牌（非阻塞）
     * @param key 限流键
     * @param capacity 桶容量
     * @param rate 令牌填充速率（每秒）
     * @param tokens 请求令牌数
     * @return 是否获取成功
     */
    boolean tryAcquire(String key, int capacity, double rate, int tokens);
    
    /**
     * 尝试获取令牌（阻塞等待）
     * @param key 限流键
     * @param capacity 桶容量
     * @param rate 令牌填充速率（每秒）
     * @param timeout 等待超时时间（毫秒）
     * @return 是否获取成功
     */
    boolean tryAcquireWithTimeout(String key, int capacity, double rate, long timeout);
    
    /**
     * 获取当前令牌数量
     * @param key 限流键
     * @return 剩余令牌数量
     */
    int getCurrentTokens(String key);
    
    /**
     * 重置令牌桶
     * @param key 限流键
     * @return 是否重置成功
     */
    boolean resetTokenBucket(String key);
    
    /**
     * 检查是否被限流
     * @param key 限流键
     * @param capacity 桶容量
     * @param rate 令牌填充速率（每秒）
     * @return 是否被限流
     */
    boolean isRateLimited(String key, int capacity, double rate);
    
    /**
     * 获取令牌桶信息
     * @param key 限流键
     * @return 令牌桶信息
     */
    TokenBucketInfo getTokenBucketInfo(String key);
    
    /**
     * 预热令牌桶（填充到指定数量）
     * @param key 限流键
     * @param capacity 桶容量
     * @param rate 令牌填充速率（每秒）
     * @param warmupTokens 预热令牌数
     * @return 是否预热成功
     */
    boolean warmupTokenBucket(String key, int capacity, double rate, int warmupTokens);
    
    /**
     * 令牌桶信息类
     */
    class TokenBucketInfo {
        private String key;
        private int currentTokens;
        private int capacity;
        private double rate;
        private long lastRefillTime;
        private long nextRefillTime;
        private long ttl;
        
        public TokenBucketInfo(String key, int currentTokens, int capacity, double rate, 
                              long lastRefillTime, long nextRefillTime, long ttl) {
            this.key = key;
            this.currentTokens = currentTokens;
            this.capacity = capacity;
            this.rate = rate;
            this.lastRefillTime = lastRefillTime;
            this.nextRefillTime = nextRefillTime;
            this.ttl = ttl;
        }
        
        // Getters
        public String getKey() { return key; }
        public int getCurrentTokens() { return currentTokens; }
        public int getCapacity() { return capacity; }
        public double getRate() { return rate; }
        public long getLastRefillTime() { return lastRefillTime; }
        public long getNextRefillTime() { return nextRefillTime; }
        public long getTtl() { return ttl; }
        
        /**
         * 获取令牌桶使用率
         * @return 使用率（0.0-1.0）
         */
        public double getUsageRate() {
            return capacity > 0 ? (double) currentTokens / capacity : 0.0;
        }
        
        /**
         * 获取下次填充剩余时间（秒）
         * @return 剩余时间
         */
        public long getTimeToNextRefill() {
            long now = System.currentTimeMillis() / 1000;
            return Math.max(0, nextRefillTime - now);
        }
        
        @Override
        public String toString() {
            return String.format("TokenBucketInfo{key='%s', currentTokens=%d, capacity=%d, rate=%.2f, " +
                               "lastRefillTime=%d, nextRefillTime=%d, ttl=%d, usageRate=%.2f}", 
                key, currentTokens, capacity, rate, lastRefillTime, nextRefillTime, ttl, getUsageRate());
        }
    }
}
