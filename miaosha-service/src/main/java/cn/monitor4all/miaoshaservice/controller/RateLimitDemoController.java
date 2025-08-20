package cn.monitor4all.miaoshaservice.controller;

import cn.monitor4all.miaoshaservice.service.DistributedRateLimitService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

/**
 * 分布式限流演示控制器
 * 展示如何使用分布式限流注解和服务
 */
@RestController
@RequestMapping("/api/rate-limit-demo")
@CrossOrigin
public class RateLimitDemoController {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(RateLimitDemoController.class);
    
    @Resource
    private DistributedRateLimitService distributedRateLimitService;
    
    /**
     * 使用注解进行限流：滑动时间窗口算法
     * 每分钟最多10次请求
     */
    @GetMapping("/sliding-window")
    public Map<String, Object> slidingWindowDemo() {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "滑动时间窗口限流测试成功");
        result.put("timestamp", System.currentTimeMillis());
        LOGGER.info("滑动时间窗口限流测试成功");
        return result;
    }
    
    /**
     * 使用注解进行限流：令牌桶算法
     * 桶容量10，每秒填充2个令牌
     */
    @GetMapping("/token-bucket")
    public Map<String, Object> tokenBucketDemo() {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "令牌桶限流测试成功");
        result.put("timestamp", System.currentTimeMillis());
        LOGGER.info("令牌桶限流测试成功");
        return result;
    }
    
    /**
     * 使用注解进行限流：基于用户ID的个性化限流
     * 每个用户每分钟最多5次请求
     */
    @GetMapping("/user-specific")
    public Map<String, Object> userSpecificDemo(@RequestParam String userId) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "用户个性化限流测试成功");
        result.put("userId", userId);
        result.put("timestamp", System.currentTimeMillis());
        LOGGER.info("用户{}的个性化限流测试成功", userId);
        return result;
    }
    
    /**
     * 使用注解进行限流：阻塞等待模式
     * 最多等待2秒
     */
    @GetMapping("/blocking")
    public Map<String, Object> blockingDemo() {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "阻塞等待限流测试成功");
        result.put("timestamp", System.currentTimeMillis());
        LOGGER.info("阻塞等待限流测试成功");
        return result;
    }
    
    /**
     * 手动调用限流服务：滑动时间窗口
     */
    @GetMapping("/manual/sliding-window")
    public Map<String, Object> manualSlidingWindow(@RequestParam String key) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // 尝试获取令牌：每分钟最多10次
            boolean allowed = distributedRateLimitService.tryAcquire(key, 10, 60);
            
            if (allowed) {
                result.put("success", true);
                result.put("message", "手动滑动时间窗口限流：通过");
                result.put("key", key);
                result.put("currentTokens", distributedRateLimitService.getCurrentTokens(key));
                LOGGER.info("手动滑动时间窗口限流通过，键: {}", key);
            } else {
                result.put("success", false);
                result.put("message", "手动滑动时间窗口限流：拒绝");
                result.put("key", key);
                result.put("currentTokens", distributedRateLimitService.getCurrentTokens(key));
                LOGGER.warn("手动滑动时间窗口限流拒绝，键: {}", key);
            }
            
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "限流检查异常: " + e.getMessage());
            LOGGER.error("手动滑动时间窗口限流异常，键: {}", key, e);
        }
        
        result.put("timestamp", System.currentTimeMillis());
        return result;
    }
    
    /**
     * 手动调用限流服务：令牌桶
     */
    @GetMapping("/manual/token-bucket")
    public Map<String, Object> manualTokenBucket(@RequestParam String key) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // 尝试获取令牌：桶容量10，每秒填充2个令牌
            boolean allowed = ((cn.monitor4all.miaoshaservice.service.impl.DistributedRateLimitServiceImpl) distributedRateLimitService)
                .tryAcquireWithTokenBucket(key, 10, 2.0, 1);
            
            if (allowed) {
                result.put("success", true);
                result.put("message", "手动令牌桶限流：通过");
                result.put("key", key);
                LOGGER.info("手动令牌桶限流通过，键: {}", key);
            } else {
                result.put("success", false);
                result.put("message", "手动令牌桶限流：拒绝");
                result.put("key", key);
                LOGGER.warn("手动令牌桶限流拒绝，键: {}", key);
            }
            
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "限流检查异常: " + e.getMessage());
            LOGGER.error("手动令牌桶限流异常，键: {}", key, e);
        }
        
        result.put("timestamp", System.currentTimeMillis());
        return result;
    }
    
    /**
     * 获取限流信息
     */
    @GetMapping("/info/{key}")
    public Map<String, Object> getRateLimitInfo(@PathVariable String key) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            DistributedRateLimitService.RateLimitInfo info = distributedRateLimitService.getRateLimitInfo(key);
            
            result.put("success", true);
            result.put("key", info.getKey());
            result.put("remainingTokens", info.getRemainingTokens());
            result.put("resetTime", info.getResetTime());
            result.put("limit", info.getLimit());
            result.put("window", info.getWindow());
            
            LOGGER.info("获取限流信息成功，键: {}", key);
            
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "获取限流信息异常: " + e.getMessage());
            LOGGER.error("获取限流信息异常，键: {}", key, e);
        }
        
        result.put("timestamp", System.currentTimeMillis());
        return result;
    }
    
    /**
     * 重置限流计数器
     */
    @PostMapping("/reset/{key}")
    public Map<String, Object> resetRateLimit(@PathVariable String key) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            boolean success = distributedRateLimitService.resetRateLimit(key);
            
            result.put("success", success);
            result.put("message", success ? "重置限流计数器成功" : "重置限流计数器失败");
            result.put("key", key);
            
            LOGGER.info("重置限流计数器，键: {}, 结果: {}", key, success);
            
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "重置限流计数器异常: " + e.getMessage());
            LOGGER.error("重置限流计数器异常，键: {}", key, e);
        }
        
        result.put("timestamp", System.currentTimeMillis());
        return result;
    }
}
