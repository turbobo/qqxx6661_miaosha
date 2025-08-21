package cn.monitor4all.miaoshaservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 分布式令牌桶限流配置类
 * 提供令牌桶限流的配置参数
 */
@Component
@ConfigurationProperties(prefix = "miaosha.rate-limit.token-bucket")
public class DistributedTokenBucketConfig {
    
    /**
     * 默认桶容量
     */
    private int defaultCapacity = 100;
    
    /**
     * 默认令牌填充速率（每秒）
     */
    private double defaultRate = 10.0;
    
    /**
     * 默认预热令牌数
     */
    private int defaultWarmupTokens = 50;
    
    /**
     * 是否启用预热
     */
    private boolean enableWarmup = true;
    
    /**
     * 预热延迟时间（毫秒）
     */
    private long warmupDelay = 5000;
    
    /**
     * 接口级别的令牌桶配置
     * key: 接口路径或标识
     * value: 配置信息
     */
    private Map<String, TokenBucketRule> rules = new HashMap<>();
    
    /**
     * 用户级别的令牌桶配置
     */
    private TokenBucketRule userRule = new TokenBucketRule(20, 2.0, 10);
    
    /**
     * 全局级别的令牌桶配置
     */
    private TokenBucketRule globalRule = new TokenBucketRule(1000, 100.0, 500);
    
    /**
     * 令牌桶规则配置
     */
    public static class TokenBucketRule {
        private int capacity;
        private double rate;
        private int warmupTokens;
        
        public TokenBucketRule() {}
        
        public TokenBucketRule(int capacity, double rate, int warmupTokens) {
            this.capacity = capacity;
            this.rate = rate;
            this.warmupTokens = warmupTokens;
        }
        
        // Getters and Setters
        public int getCapacity() { return capacity; }
        public void setCapacity(int capacity) { this.capacity = capacity; }
        
        public double getRate() { return rate; }
        public void setRate(double rate) { this.rate = rate; }
        
        public int getWarmupTokens() { return warmupTokens; }
        public void setWarmupTokens(int warmupTokens) { this.warmupTokens = warmupTokens; }
        
        @Override
        public String toString() {
            return String.format("TokenBucketRule{capacity=%d, rate=%.2f, warmupTokens=%d}", 
                capacity, rate, warmupTokens);
        }
    }
    
    // Getters and Setters
    public int getDefaultCapacity() { return defaultCapacity; }
    public void setDefaultCapacity(int defaultCapacity) { this.defaultCapacity = defaultCapacity; }
    
    public double getDefaultRate() { return defaultRate; }
    public void setDefaultRate(double defaultRate) { this.defaultRate = defaultRate; }
    
    public int getDefaultWarmupTokens() { return defaultWarmupTokens; }
    public void setDefaultWarmupTokens(int defaultWarmupTokens) { this.defaultWarmupTokens = defaultWarmupTokens; }
    
    public boolean isEnableWarmup() { return enableWarmup; }
    public void setEnableWarmup(boolean enableWarmup) { this.enableWarmup = enableWarmup; }
    
    public long getWarmupDelay() { return warmupDelay; }
    public void setWarmupDelay(long warmupDelay) { this.warmupDelay = warmupDelay; }
    
    public Map<String, TokenBucketRule> getRules() { return rules; }
    public void setRules(Map<String, TokenBucketRule> rules) { this.rules = rules; }
    
    public TokenBucketRule getUserRule() { return userRule; }
    public void setUserRule(TokenBucketRule userRule) { this.userRule = userRule; }
    
    public TokenBucketRule getGlobalRule() { return globalRule; }
    public void setGlobalRule(TokenBucketRule globalRule) { this.globalRule = globalRule; }
    
    /**
     * 获取指定接口的令牌桶配置
     * @param interfaceKey 接口标识
     * @return 令牌桶配置规则
     */
    public TokenBucketRule getRule(String interfaceKey) {
        return rules.getOrDefault(interfaceKey, 
            new TokenBucketRule(defaultCapacity, defaultRate, defaultWarmupTokens));
    }
    
    /**
     * 获取用户级别的令牌桶配置
     * @return 用户令牌桶配置
     */
    public TokenBucketRule getUserTokenBucketRule() {
        return userRule;
    }
    
    /**
     * 获取全局级别的令牌桶配置
     * @return 全局令牌桶配置
     */
    public TokenBucketRule getGlobalTokenBucketRule() {
        return globalRule;
    }
    
    @Override
    public String toString() {
        return String.format("DistributedTokenBucketConfig{defaultCapacity=%d, defaultRate=%.2f, " +
                           "defaultWarmupTokens=%d, enableWarmup=%s, warmupDelay=%d, rules=%s, " +
                           "userRule=%s, globalRule=%s}", 
            defaultCapacity, defaultRate, defaultWarmupTokens, enableWarmup, warmupDelay, 
            rules, userRule, globalRule);
    }
}
