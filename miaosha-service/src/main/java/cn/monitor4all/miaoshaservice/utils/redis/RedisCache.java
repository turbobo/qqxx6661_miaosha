package cn.monitor4all.miaoshaservice.utils.redis;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

/**
 * @Author
 * @Date 2025/9/2 14:13
 */
@Component
@Slf4j
public class RedisCache {

    @Resource(name = "redisTemplate")
    private RedisTemplate<String, Object> jedisTemplate;
    
    private static RedisTemplate<String, Object> redisTemplateStatic;

    @PostConstruct
    public void init() {
        redisTemplateStatic = jedisTemplate;
    }

    public static RedisLock createRedisLock(String key, int expireTime, long timeOut) {
        return new RedisLock(redisTemplateStatic, key, expireTime, timeOut);
    }
}
