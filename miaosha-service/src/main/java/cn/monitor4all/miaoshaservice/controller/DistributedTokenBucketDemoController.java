package cn.monitor4all.miaoshaservice.controller;

import cn.monitor4all.miaoshaservice.annotation.DistributedTokenBucketLimit;
import cn.monitor4all.miaoshaservice.config.DistributedTokenBucketConfig;
import cn.monitor4all.miaoshaservice.service.DistributedTokenBucketService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

/**
 * 分布式令牌桶限流演示控制器
 * 展示如何使用分布式令牌桶限流注解和服务
 */
@RestController
@RequestMapping("/api/token-bucket-demo")
public class DistributedTokenBucketDemoController {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(DistributedTokenBucketDemoController.class);
    
    @Resource
    private DistributedTokenBucketService distributedTokenBucketService;
    
    @Resource
    private DistributedTokenBucketConfig config;
    
    /**
     * 接口级别限流测试：每秒最多10个请求
     */
    @GetMapping("/interface-limit")
    @DistributedTokenBucketLimit(
        capacity = 10,
        rate = 10.0,
        strategy = DistributedTokenBucketLimit.Strategy.INTERFACE,
        message = "接口限流：每秒最多10个请求"
    )
    public Map<String, Object> testInterfaceLimit() {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "接口级别限流测试成功");
        result.put("timestamp", System.currentTimeMillis());
        
        LOGGER.info("接口级别限流测试成功");
        return result;
    }
    
    /**
     * 用户级别限流测试：每个用户每秒最多2个请求
     */
    @GetMapping("/user-limit/{userId}")
    @DistributedTokenBucketLimit(
        capacity = 20,
        rate = 2.0,
        strategy = DistributedTokenBucketLimit.Strategy.USER,
        message = "用户限流：每个用户每秒最多2个请求"
    )
    public Map<String, Object> testUserLimit(@PathVariable Long userId) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "用户级别限流测试成功");
        result.put("userId", userId);
        result.put("timestamp", System.currentTimeMillis());
        
        LOGGER.info("用户{}的用户级别限流测试成功", userId);
        return result;
    }
    
    /**
     * 全局级别限流测试：全局每秒最多100个请求
     */
    @GetMapping("/global-limit")
    @DistributedTokenBucketLimit(
        capacity = 1000,
        rate = 100.0,
        strategy = DistributedTokenBucketLimit.Strategy.GLOBAL,
        message = "全局限流：全局每秒最多100个请求"
    )
    public Map<String, Object> testGlobalLimit() {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "全局级别限流测试成功");
        result.put("timestamp", System.currentTimeMillis());
        
        LOGGER.info("全局级别限流测试成功");
        return result;
    }
    
    /**
     * 自定义限流键测试：使用SpEL表达式
     */
    @PostMapping("/custom-limit")
    @DistributedTokenBucketLimit(
        key = "'custom:' + #request.userId + ':' + #request.interface",
        capacity = 50,
        rate = 5.0,
        strategy = DistributedTokenBucketLimit.Strategy.CUSTOM,
        message = "自定义限流：基于用户ID和接口的组合限流"
    )
    public Map<String, Object> testCustomLimit(@RequestBody CustomLimitRequest request) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "自定义限流测试成功");
        result.put("request", request);
        result.put("timestamp", System.currentTimeMillis());
        
        LOGGER.info("用户{}的自定义限流测试成功，接口: {}", request.getUserId(), request.getInterface());
        return result;
    }
    
    /**
     * 阻塞等待限流测试：等待获取令牌
     */
    @GetMapping("/blocking-limit")
    @DistributedTokenBucketLimit(
        capacity = 5,
        rate = 1.0,
        blocking = true,
        timeout = 5000,
        message = "阻塞等待限流：最多等待5秒获取令牌"
    )
    public Map<String, Object> testBlockingLimit() {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "阻塞等待限流测试成功");
        result.put("timestamp", System.currentTimeMillis());
        
        LOGGER.info("阻塞等待限流测试成功");
        return result;
    }
    
    /**
     * 手动调用令牌桶限流服务
     */
    @GetMapping("/manual-limit/{key}")
    public Map<String, Object> testManualLimit(@PathVariable String key) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // 获取配置
            DistributedTokenBucketConfig.TokenBucketRule rule = config.getRule(key);
            
            // 尝试获取令牌
            boolean allowed = distributedTokenBucketService.tryAcquire(key, rule.getCapacity(), rule.getRate());
            
            if (allowed) {
                result.put("success", true);
                result.put("message", "手动令牌桶限流：通过");
                result.put("key", key);
                result.put("capacity", rule.getCapacity());
                result.put("rate", rule.getRate());
                result.put("currentTokens", distributedTokenBucketService.getCurrentTokens(key));
                
                LOGGER.info("手动令牌桶限流通过，键: {}", key);
            } else {
                result.put("success", false);
                result.put("message", "手动令牌桶限流：拒绝");
                result.put("key", key);
                result.put("capacity", rule.getCapacity());
                result.put("rate", rule.getRate());
                result.put("currentTokens", distributedTokenBucketService.getCurrentTokens(key));
                
                LOGGER.warn("手动令牌桶限流拒绝，键: {}", key);
            }
            
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "限流检查异常: " + e.getMessage());
            result.put("key", key);
            
            LOGGER.error("手动令牌桶限流异常，键: {}", key, e);
        }
        
        return result;
    }
    
    /**
     * 获取令牌桶信息
     */
    @GetMapping("/info/{key}")
    public Map<String, Object> getTokenBucketInfo(@PathVariable String key) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            DistributedTokenBucketService.TokenBucketInfo info = distributedTokenBucketService.getTokenBucketInfo(key);
            
            result.put("success", true);
            result.put("message", "获取令牌桶信息成功");
            result.put("info", info);
            
            LOGGER.info("获取令牌桶信息成功，键: {}", key);
            
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "获取令牌桶信息异常: " + e.getMessage());
            result.put("key", key);
            
            LOGGER.error("获取令牌桶信息异常，键: {}", key, e);
        }
        
        return result;
    }
    
    /**
     * 重置令牌桶
     */
    @PostMapping("/reset/{key}")
    public Map<String, Object> resetTokenBucket(@PathVariable String key) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            boolean success = distributedTokenBucketService.resetTokenBucket(key);
            
            result.put("success", success);
            result.put("message", success ? "重置令牌桶成功" : "重置令牌桶失败");
            result.put("key", key);
            
            LOGGER.info("重置令牌桶，键: {}, 结果: {}", key, success);
            
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "重置令牌桶异常: " + e.getMessage());
            result.put("key", key);
            
            LOGGER.error("重置令牌桶异常，键: {}", key, e);
        }
        
        return result;
    }
    
    /**
     * 预热令牌桶
     */
    @PostMapping("/warmup/{key}")
    public Map<String, Object> warmupTokenBucket(@PathVariable String key) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            DistributedTokenBucketConfig.TokenBucketRule rule = config.getRule(key);
            boolean success = distributedTokenBucketService.warmupTokenBucket(
                key, rule.getCapacity(), rule.getRate(), rule.getWarmupTokens());
            
            result.put("success", success);
            result.put("message", success ? "预热令牌桶成功" : "预热令牌桶失败");
            result.put("key", key);
            result.put("rule", rule);
            
            LOGGER.info("预热令牌桶，键: {}, 结果: {}", key, success);
            
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "预热令牌桶异常: " + e.getMessage());
            result.put("key", key);
            
            LOGGER.error("预热令牌桶异常，键: {}", key, e);
        }
        
        return result;
    }
    
    /**
     * 自定义限流请求对象
     */
    public static class CustomLimitRequest {
        private Long userId;
        private String interfaceName;
        
        // Getters and Setters
        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        
        public String getInterface() { return interfaceName; }
        public void setInterface(String interfaceName) { this.interfaceName = interfaceName; }
        
        @Override
        public String toString() {
            return String.format("CustomLimitRequest{userId=%d, interface='%s'}", userId, interfaceName);
        }
    }
}
