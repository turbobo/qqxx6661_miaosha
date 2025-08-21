package cn.monitor4all.miaoshaservice.service.impl;

import cn.monitor4all.miaoshaservice.service.DistributedTokenBucketService;
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
 * 分布式令牌桶限流服务实现类
 * 基于Redis+Lua脚本实现分布式环境下的令牌桶限流
 */
@Service
public class DistributedTokenBucketServiceImpl implements DistributedTokenBucketService {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(DistributedTokenBucketServiceImpl.class);
    
    // 令牌桶键前缀
    private static final String TOKEN_BUCKET_PREFIX = "token_bucket:";
    
    // 默认令牌数
    private static final int DEFAULT_TOKENS = 1;
    
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    
    // 令牌桶限流脚本
    private RedisScript<List> tokenBucketScript;
    
    @PostConstruct
    public void init() {
        try {
            // 加载令牌桶限流脚本
            ClassPathResource tokenBucketResource = new ClassPathResource("scripts/token_bucket.lua");
            tokenBucketScript = new DefaultRedisScript<>();
            ((DefaultRedisScript<List>) tokenBucketScript).setLocation(tokenBucketResource);
            ((DefaultRedisScript<List>) tokenBucketScript).setResultType(List.class);
            
            LOGGER.info("分布式令牌桶限流服务初始化成功");
        } catch (Exception e) {
            LOGGER.error("分布式令牌桶限流服务初始化失败", e);
        }
    }
    
    @Override
    public boolean tryAcquire(String key, int capacity, double rate) {
        return tryAcquire(key, capacity, rate, DEFAULT_TOKENS);
    }
    
    @Override
    public boolean tryAcquire(String key, int capacity, double rate, int tokens) {
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
    
    @Override
    public boolean tryAcquireWithTimeout(String key, int capacity, double rate, long timeout) {
        if (timeout <= 0) {
            return tryAcquire(key, capacity, rate);
        }
        
        long startTime = System.currentTimeMillis();
        long endTime = startTime + timeout;
        
        while (System.currentTimeMillis() < endTime) {
            if (tryAcquire(key, capacity, rate)) {
                return true;
            }
            
            try {
                // 计算等待时间，避免过度轮询
                long waitTime = Math.min(100, endTime - System.currentTimeMillis());
                if (waitTime > 0) {
                    Thread.sleep(waitTime);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOGGER.warn("令牌桶限流等待被中断，键: {}", key);
                return false;
            }
        }
        
        LOGGER.debug("令牌桶限流等待超时，键: {}, 超时时间: {}ms", key, timeout);
        return false;
    }
    
    @Override
    public int getCurrentTokens(String key) {
        try {
            String fullKey = TOKEN_BUCKET_PREFIX + key;
            Object tokens = stringRedisTemplate.opsForHash().get(fullKey, "tokens");
            
            if (tokens != null) {
                return Integer.parseInt(tokens.toString());
            }
            
            return 0;
        } catch (Exception e) {
            LOGGER.error("获取当前令牌数量失败，键: {}", key, e);
            return 0;
        }
    }
    
    @Override
    public boolean resetTokenBucket(String key) {
        try {
            String fullKey = TOKEN_BUCKET_PREFIX + key;
            Boolean deleted = stringRedisTemplate.delete(fullKey);
            
            if (Boolean.TRUE.equals(deleted)) {
                LOGGER.info("重置令牌桶成功，键: {}", key);
                return true;
            } else {
                LOGGER.warn("重置令牌桶失败，键: {}", key);
                return false;
            }
        } catch (Exception e) {
            LOGGER.error("重置令牌桶异常，键: {}", key, e);
            return false;
        }
    }
    
    @Override
    public boolean isRateLimited(String key, int capacity, double rate) {
        return !tryAcquire(key, capacity, rate);
    }
    
    @Override
    public TokenBucketInfo getTokenBucketInfo(String key) {
        try {
            String fullKey = TOKEN_BUCKET_PREFIX + key;
            List<Object> bucket = stringRedisTemplate.opsForHash().multiGet(fullKey, 
                Arrays.asList("tokens", "lastRefillTime"));
            
            if (bucket != null && bucket.size() >= 2) {
                int tokens = bucket.get(0) != null ? Integer.parseInt(bucket.get(0).toString()) : 0;
                long lastRefillTime = bucket.get(1) != null ? Long.parseLong(bucket.get(1).toString()) : 0;
                Long ttl = stringRedisTemplate.getExpire(fullKey, TimeUnit.SECONDS);
                
                // 从key中解析capacity和rate（这里简化处理，实际可以从配置或缓存中获取）
                int defaultCapacity = 100; // 默认值，实际应该从配置获取
                double defaultRate = 10.0; // 默认值，实际应该从配置获取
                
                // 计算下次填充时间
                long now = System.currentTimeMillis() / 1000;
                long nextRefillTime = lastRefillTime + (long) Math.ceil(1.0 / defaultRate);
                
                return new TokenBucketInfo(key, tokens, defaultCapacity, defaultRate, lastRefillTime, nextRefillTime, 
                    ttl != null ? ttl : 0);
            }
            
            return new TokenBucketInfo(key, 0, 0, 0.0, 0, 0, 0);
            
        } catch (Exception e) {
            LOGGER.error("获取令牌桶信息失败，键: {}", key, e);
            return new TokenBucketInfo(key, 0, 0, 0.0, 0, 0, 0);
        }
    }
    
    @Override
    public boolean warmupTokenBucket(String key, int capacity, double rate, int warmupTokens) {
        try {
            String fullKey = TOKEN_BUCKET_PREFIX + key;
            long now = System.currentTimeMillis() / 1000;
            
            // 预热令牌桶，填充到指定数量
            java.util.Map<String, String> bucketData = new java.util.HashMap<>();
            bucketData.put("tokens", String.valueOf(warmupTokens));
            bucketData.put("lastRefillTime", String.valueOf(now));
            stringRedisTemplate.opsForHash().putAll(fullKey, bucketData);
            
            // 设置过期时间
            long expireTime = (long) Math.ceil(capacity / rate) + 10;
            stringRedisTemplate.expire(fullKey, expireTime, TimeUnit.SECONDS);
            
            LOGGER.info("令牌桶预热成功，键: {}, 预热令牌数: {}, 容量: {}, 填充速率: {}", 
                key, warmupTokens, capacity, rate);
            return true;
            
        } catch (Exception e) {
            LOGGER.error("令牌桶预热失败，键: {}, 错误: {}", key, e.getMessage(), e);
            return false;
        }
    }
}
