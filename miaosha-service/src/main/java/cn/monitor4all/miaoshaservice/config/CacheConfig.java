package cn.monitor4all.miaoshaservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 缓存配置类
 * 用于配置缓存相关的参数，如延迟双删的时间间隔等
 */
@Component
@ConfigurationProperties(prefix = "cache")
public class CacheConfig {
    
    /**
     * 延迟双删的延迟时间（毫秒）
     * 默认500毫秒
     */
    private long delayedDeleteDelay = 500;
    
    /**
     * 是否启用延迟双删
     * 默认启用
     */
    private boolean delayedDeleteEnabled = true;
    
    /**
     * 缓存过期时间（秒）
     * 默认3600秒（1小时）
     */
    private long expireTime = 3600;
    
    /**
     * 缓存最大重试次数
     * 默认3次
     */
    private int maxRetryCount = 3;
    
    /**
     * 缓存重试间隔（毫秒）
     * 默认100毫秒
     */
    private long retryInterval = 100;

    // Getter和Setter方法
    public long getDelayedDeleteDelay() {
        return delayedDeleteDelay;
    }

    public void setDelayedDeleteDelay(long delayedDeleteDelay) {
        this.delayedDeleteDelay = delayedDeleteDelay;
    }

    public boolean isDelayedDeleteEnabled() {
        return delayedDeleteEnabled;
    }

    public void setDelayedDeleteEnabled(boolean delayedDeleteEnabled) {
        this.delayedDeleteEnabled = delayedDeleteEnabled;
    }

    public long getExpireTime() {
        return expireTime;
    }

    public void setExpireTime(long expireTime) {
        this.expireTime = expireTime;
    }

    public int getMaxRetryCount() {
        return maxRetryCount;
    }

    public void setMaxRetryCount(int maxRetryCount) {
        this.maxRetryCount = maxRetryCount;
    }

    public long getRetryInterval() {
        return retryInterval;
    }

    public void setRetryInterval(long retryInterval) {
        this.retryInterval = retryInterval;
    }
}
