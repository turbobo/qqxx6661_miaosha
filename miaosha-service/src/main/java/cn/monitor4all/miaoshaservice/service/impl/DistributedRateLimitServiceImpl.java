package cn.monitor4all.miaoshaservice.service.impl;

import cn.monitor4all.miaoshaservice.service.DistributedRateLimitService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 分布式限流服务实现类
 * 基于Redis+Lua脚本实现分布式环境下的接口限流
 */
@Service
public class DistributedRateLimitServiceImpl implements DistributedRateLimitService {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(DistributedRateLimitServiceImpl.class);
    
    // 限流键前缀
    private static final String RATE_LIMIT_PREFIX = "rate_limit:";
    
    // 令牌桶键前缀
    private static final String TOKEN_BUCKET_PREFIX = "token_bucket:";
    
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    
    // 滑动时间窗口限流脚本
    private RedisScript<List> rateLimitScript;
    
    // 令牌桶限流脚本
    private RedisScript<List> tokenBucketScript;
    
    @PostConstruct
    public void init() {
        try {
            // 加载滑动时间窗口限流脚本
            ClassPathResource rateLimitResource = new ClassPathResource("scripts/rate_limit.lua");
            rateLimitScript = new DefaultRedisScript<>();
            ((DefaultRedisScript<List>) rateLimitScript).setLocation(rateLimitResource);
            ((DefaultRedisScript<List>) rateLimitScript).setResultType(List.class);
            
            // 加载令牌桶限流脚本
            ClassPathResource tokenBucketResource = new ClassPathResource("scripts/token_bucket.lua");
            tokenBucketScript = new DefaultRedisScript<>();
            ((DefaultRedisScript<List>) tokenBucketScript).setLocation(tokenBucketResource);
            ((DefaultRedisScript<List>) tokenBucketScript).setResultType(List.class);
            
            LOGGER.info("分布式限流服务初始化成功");
        } catch (Exception e) {
            LOGGER.error("分布式限流服务初始化失败", e);
        }
    }
    
    @Override
    public boolean tryAcquire(String key, int limit, int window) {
        return tryAcquire(key, limit, window, 0);
    }
    
    @Override
    public boolean tryAcquire(String key, int limit, int window, long timeout) {
        try {
            String fullKey = RATE_LIMIT_PREFIX + key;
            long now = System.currentTimeMillis() / 1000;
            
            // 执行Lua脚本
            List<Object> result = stringRedisTemplate.execute(
                rateLimitScript,
                Arrays.asList(fullKey),
                String.valueOf(limit),
                String.valueOf(window),
                String.valueOf(now)
            );
            
            if (result != null && result.size() >= 3) {
                int success = ((Number) result.get(0)).intValue();
                int currentCount = ((Number) result.get(1)).intValue();
                long resetTime = ((Number) result.get(2)).longValue();
                
                if (success == 1) {
                    LOGGER.debug("限流通过，键: {}, 当前计数: {}, 重置时间: {}", key, currentCount, resetTime);
                    return true;
                } else {
                    LOGGER.debug("限流拒绝，键: {}, 当前计数: {}, 重置时间: {}", key, currentCount, resetTime);
                    return false;
                }
            }
            
            LOGGER.warn("限流脚本执行结果异常，键: {}", key);
            return false;
            
        } catch (Exception e) {
            LOGGER.error("执行限流脚本失败，键: {}, 错误: {}", key, e.getMessage(), e);
            // 限流失败时，为了安全起见，拒绝请求
            return false;
        }
    }
    
    @Override
    public int getCurrentTokens(String key) {
        try {
            String fullKey = RATE_LIMIT_PREFIX + key;
            Long count = stringRedisTemplate.opsForZSet().zCard(fullKey);
            return count != null ? count.intValue() : 0;
        } catch (Exception e) {
            LOGGER.error("获取当前令牌数失败，键: {}", key, e);
            return 0;
        }
    }
    
    @Override
    public boolean resetRateLimit(String key) {
        try {
            String fullKey = RATE_LIMIT_PREFIX + key;
            Boolean deleted = stringRedisTemplate.delete(fullKey);
            LOGGER.info("重置限流计数器，键: {}, 结果: {}", key, deleted);
            return Boolean.TRUE.equals(deleted);
        } catch (Exception e) {
            LOGGER.error("重置限流计数器失败，键: {}", key, e);
            return false;
        }
    }
    
    @Override
    public boolean isRateLimited(String key, int limit, int window) {
        return !tryAcquire(key, limit, window);
    }
    
    @Override
    public RateLimitInfo getRateLimitInfo(String key) {
        try {
            String fullKey = RATE_LIMIT_PREFIX + key;
            Long count = stringRedisTemplate.opsForZSet().zCard(fullKey);
            Long ttl = stringRedisTemplate.getExpire(fullKey, TimeUnit.SECONDS);
            
            int currentCount = count != null ? count.intValue() : 0;
            long resetTime = ttl != null ? System.currentTimeMillis() / 1000 + ttl : 0;
            
            // 这里需要根据实际业务逻辑确定limit和window
            // 暂时使用默认值，实际使用时应该从配置或参数中获取
            int defaultLimit = 10;
            int defaultWindow = 60;
            
            return new RateLimitInfo(key, defaultLimit - currentCount, resetTime, defaultLimit, defaultWindow);
            
        } catch (Exception e) {
            LOGGER.error("获取限流信息失败，键: {}", key, e);
            return new RateLimitInfo(key, 0, 0, 0, 0);
        }
    }
    
    /**
     * 使用令牌桶算法进行限流
     * @param key 限流键
     * @param capacity 桶容量
     * @param rate 令牌填充速率（每秒）
     * @param tokens 请求令牌数
     * @return 是否获取成功
     */
    public boolean tryAcquireWithTokenBucket(String key, int capacity, double rate, int tokens) {
        try {
            String fullKey = TOKEN_BUCKET_PREFIX + key;
            long now = System.currentTimeMillis() / 1000;
            
            // 执行令牌桶Lua脚本
            List<Object> result = stringRedisTemplate.execute(
                tokenBucketScript,
                Arrays.asList(fullKey),
                String.valueOf(capacity),
                String.valueOf(rate),
                String.valueOf(now),
                String.valueOf(tokens)
            );
            
            if (result != null && result.size() >= 3) {
                int success = ((Number) result.get(0)).intValue();
                int remainingTokens = ((Number) result.get(1)).intValue();
                long nextRefillTime = ((Number) result.get(2)).longValue();
                
                if (success == 1) {
                    LOGGER.debug("令牌桶限流通过，键: {}, 剩余令牌: {}, 下次填充时间: {}", 
                        key, remainingTokens, nextRefillTime);
                    return true;
                } else {
                    LOGGER.debug("令牌桶限流拒绝，键: {}, 剩余令牌: {}, 下次填充时间: {}", 
                        key, remainingTokens, nextRefillTime);
                    return false;
                }
            }
            
            LOGGER.warn("令牌桶限流脚本执行结果异常，键: {}", key);
            return false;
            
        } catch (Exception e) {
            LOGGER.error("执行令牌桶限流脚本失败，键: {}, 错误: {}", key, e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 获取令牌桶信息
     * @param key 限流键
     * @return 令牌桶信息
     */
    public TokenBucketInfo getTokenBucketInfo(String key) {
        try {
            String fullKey = TOKEN_BUCKET_PREFIX + key;
            List<Object> bucket = stringRedisTemplate.opsForHash().multiGet(fullKey, Arrays.asList("tokens", "lastRefillTime"));
            
            if (bucket != null && bucket.size() >= 2) {
                int tokens = bucket.get(0) != null ? Integer.parseInt(bucket.get(0).toString()) : 0;
                long lastRefillTime = bucket.get(1) != null ? Long.parseLong(bucket.get(1).toString()) : 0;
                Long ttl = stringRedisTemplate.getExpire(fullKey, TimeUnit.SECONDS);
                
                return new TokenBucketInfo(key, tokens, lastRefillTime, ttl != null ? ttl : 0);
            }
            
            return new TokenBucketInfo(key, 0, 0, 0);
            
        } catch (Exception e) {
            LOGGER.error("获取令牌桶信息失败，键: {}", key, e);
            return new TokenBucketInfo(key, 0, 0, 0);
        }
    }
    
    /**
     * 令牌桶信息类
     */
    public static class TokenBucketInfo {
        private String key;
        private int tokens;
        private long lastRefillTime;
        private long ttl;
        
        public TokenBucketInfo(String key, int tokens, long lastRefillTime, long ttl) {
            this.key = key;
            this.tokens = tokens;
            this.lastRefillTime = lastRefillTime;
            this.ttl = ttl;
        }
        
        // Getters
        public String getKey() { return key; }
        public int getTokens() { return tokens; }
        public long getLastRefillTime() { return lastRefillTime; }
        public long getTtl() { return ttl; }
        
        @Override
        public String toString() {
            return String.format("TokenBucketInfo{key='%s', tokens=%d, lastRefillTime=%d, ttl=%d}", 
                key, tokens, lastRefillTime, ttl);
        }
    }
}
