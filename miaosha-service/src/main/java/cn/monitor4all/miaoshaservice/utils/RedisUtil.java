package cn.monitor4all.miaoshaservice.utils;

import javax.annotation.Resource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Redis工具类
 */
@Component
public class RedisUtil {
    
    @Resource
    private RedisTemplate<String, Object> redisTemplate;
    
    /**
     * 设置缓存
     * @param key 键
     * @param value 值
     */
    public void set(String key, Object value) {
        redisTemplate.opsForValue().set(key, value);
    }
    
    /**
     * 设置缓存并设置过期时间
     * @param key 键
     * @param value 值
     * @param timeout 过期时间
     * @param unit 时间单位
     */
    public void set(String key, Object value, long timeout, TimeUnit unit) {
        redisTemplate.opsForValue().set(key, value, timeout, unit);
    }
    
    /**
     * 获取缓存
     * @param key 键
     * @return 值
     */
    public Object get(String key) {
        return redisTemplate.opsForValue().get(key);
    }
    
    /**
     * 删除缓存
     * @param key 键
     */
    public void delete(String key) {
        redisTemplate.delete(key);
    }
    
    /**
     * 批量删除缓存
     * @param keys 键列表
     */
    public void delete(List<String> keys) {
        redisTemplate.delete(keys);
    }
    
    /**
     * 判断key是否存在
     * @param key 键
     * @return 是否存在
     */
    public boolean hasKey(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }
    
    /**
     * 设置过期时间
     * @param key 键
     * @param timeout 过期时间
     * @param unit 时间单位
     * @return 是否设置成功
     */
    public boolean expire(String key, long timeout, TimeUnit unit) {
        return Boolean.TRUE.equals(redisTemplate.expire(key, timeout, unit));
    }
    
    /**
     * 获取过期时间
     * @param key 键
     * @return 过期时间（秒）
     */
    public long getExpire(String key) {
        Long expire = redisTemplate.getExpire(key);
        return expire != null ? expire : -1;
    }
    
    /**
     * 获取过期时间
     * @param key 键
     * @param unit 时间单位
     * @return 过期时间
     */
    public long getExpire(String key, TimeUnit unit) {
        Long expire = redisTemplate.getExpire(key, unit);
        return expire != null ? expire : -1;
    }
    
    /**
     * 移除过期时间
     * @param key 键
     * @return 是否移除成功
     */
    public boolean persist(String key) {
        return Boolean.TRUE.equals(redisTemplate.persist(key));
    }
    
    /**
     * 递增
     * @param key 键
     * @param delta 增量
     * @return 递增后的值
     */
    public long increment(String key, long delta) {
        Long result = redisTemplate.opsForValue().increment(key, delta);
        return result != null ? result : 0;
    }
    
    /**
     * 递减
     * @param key 键
     * @param delta 减量
     * @return 递减后的值
     */
    public long decrement(String key, long delta) {
        Long result = redisTemplate.opsForValue().increment(key, -delta);
        return result != null ? result : 0;
    }
}
