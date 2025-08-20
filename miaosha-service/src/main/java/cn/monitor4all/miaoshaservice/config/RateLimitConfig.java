package cn.monitor4all.miaoshaservice.config;

import cn.monitor4all.miaoshaservice.service.DistributedRateLimitService;
import cn.monitor4all.miaoshaservice.service.impl.DistributedRateLimitServiceImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

/**
 * 限流配置类
 * 配置Redis脚本和限流服务
 */
@Configuration
public class RateLimitConfig {
    
    /**
     * 滑动时间窗口限流脚本
     */
    @Bean
    public RedisScript<List> rateLimitScript() {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("scripts/rate_limit.lua"));
        script.setResultType(List.class);
        return script;
    }
    
    /**
     * 令牌桶限流脚本
     */
    @Bean
    public RedisScript<List> tokenBucketScript() {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("scripts/token_bucket.lua"));
        script.setResultType(List.class);
        return script;
    }
    
    /**
     * 分布式限流服务
     */
    @Bean
    public DistributedRateLimitService distributedRateLimitService(StringRedisTemplate stringRedisTemplate) {
        return new DistributedRateLimitServiceImpl();
    }
}
