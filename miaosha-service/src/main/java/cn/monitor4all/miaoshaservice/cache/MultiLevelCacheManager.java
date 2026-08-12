package cn.monitor4all.miaoshaservice.cache;

import com.alibaba.fastjson.JSON;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * 多级缓存管理器
 * <p>
 * L1: Caffeine 本地缓存（TTL 10s，最大200条）
 * L2: Redis 远程缓存（TTL 由调用者指定）
 * <p>
 * 读取链路：L1 → L2(Redis) → DB(由调用者提供 loader)
 * 写入链路：同时写入 L1 和 L2
 * 失效链路：同时失效 L1 和 L2，并通过 Redis Pub/Sub 广播失效事件
 */
@Component
public class MultiLevelCacheManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(MultiLevelCacheManager.class);

    /** 缓存失效广播 Redis Channel */
    public static final String CACHE_INVALIDATE_CHANNEL = "cache:invalidate";

    /** L1 本地缓存最大条目数 */
    private static final long L1_MAX_SIZE = 200L;

    /** L1 本地缓存 TTL（秒） */
    private static final long L1_TTL_SECONDS = 10L;

    /** L1 本地缓存 */
    private final Cache<String, String> localCache;

    @Autowired
    private StringRedisTemplate redisTemplate;

    public MultiLevelCacheManager() {
        this.localCache = Caffeine.newBuilder()
                .maximumSize(L1_MAX_SIZE)
                .expireAfterWrite(L1_TTL_SECONDS, TimeUnit.SECONDS)
                .recordStats()
                .build();
        LOGGER.info("MultiLevelCacheManager 初始化完成, L1 maxSize={}, L1 TTL={}s", L1_MAX_SIZE, L1_TTL_SECONDS);
    }

    /**
     * 多级缓存读取：L1 → L2(Redis) → DB(由调用者提供 loader)
     *
     * @param key      缓存 key
     * @param type     反序列化目标类型
     * @param dbLoader 缓存未命中时的数据库加载器，返回 null 表示数据库中也不存在
     * @param <T>      缓存值类型
     * @return 缓存值或 null
     */
    public <T> T get(String key, Class<T> type, Function<String, T> dbLoader) {
        // 1. 查 L1 本地缓存
        String json = localCache.getIfPresent(key);
        if (json != null) {
            LOGGER.debug("[L1 HIT] key={}", key);
            return JSON.parseObject(json, type);
        }

        // 2. 查 L2 Redis 缓存
        try {
            json = redisTemplate.opsForValue().get(key);
            if (json != null) {
                LOGGER.debug("[L2 HIT] key={}", key);
                // 回填 L1
                localCache.put(key, json);
                return JSON.parseObject(json, type);
            }
        } catch (Exception e) {
            LOGGER.warn("[L2 ERROR] Redis 读取异常, key={}, 降级到 DB", key, e);
        }

        // 3. 查 DB
        if (dbLoader != null) {
            T value = dbLoader.apply(key);
            if (value != null) {
                LOGGER.debug("[DB HIT] key={}", key);
                // 回填 L2 + L1
                String valueJson = JSON.toJSONString(value);
                try {
                    redisTemplate.opsForValue().set(key, valueJson, 3600L, TimeUnit.SECONDS);
                } catch (Exception e) {
                    LOGGER.warn("[L2 WRITE ERROR] Redis 写入异常, key={}", key, e);
                }
                localCache.put(key, valueJson);
                return value;
            }
        }

        LOGGER.debug("[MISS] 所有层级均未命中, key={}", key);
        return null;
    }

    /**
     * 写入 L2(Redis) 和 L1 本地缓存
     *
     * @param key        缓存 key
     * @param value      缓存值（将被 JSON 序列化）
     * @param ttlSeconds Redis TTL（秒）
     */
    public void put(String key, Object value, long ttlSeconds) {
        String json = JSON.toJSONString(value);
        try {
            redisTemplate.opsForValue().set(key, json, ttlSeconds, TimeUnit.SECONDS);
            LOGGER.debug("[PUT L2] key={}, ttl={}s", key, ttlSeconds);
        } catch (Exception e) {
            LOGGER.warn("[PUT L2 ERROR] Redis 写入异常, key={}", key, e);
        }
        localCache.put(key, json);
        LOGGER.debug("[PUT L1] key={}", key);
    }

    /**
     * 同时失效 L1 和 L2，并发布失效事件到 Redis Pub/Sub
     *
     * @param key 缓存 key
     */
    public void evict(String key) {
        // 失效 L1
        localCache.invalidate(key);
        LOGGER.debug("[EVICT L1] key={}", key);

        // 失效 L2
        try {
            redisTemplate.delete(key);
            LOGGER.debug("[EVICT L2] key={}", key);
        } catch (Exception e) {
            LOGGER.warn("[EVICT L2 ERROR] Redis 删除异常, key={}", key, e);
        }

        // 发布失效事件广播
        try {
            redisTemplate.convertAndSend(CACHE_INVALIDATE_CHANNEL, key);
            LOGGER.debug("[EVICT BROADCAST] channel={}, key={}", CACHE_INVALIDATE_CHANNEL, key);
        } catch (Exception e) {
            LOGGER.warn("[EVICT BROADCAST ERROR] 广播失效事件异常, key={}", key, e);
        }
    }

    /**
     * 仅失效 L1 本地缓存（用于接收其他节点的 Pub/Sub 广播时调用）
     *
     * @param key 缓存 key
     */
    public void evictLocal(String key) {
        localCache.invalidate(key);
        LOGGER.debug("[EVICT LOCAL] key={}", key);
    }

    /**
     * 获取 L1 缓存统计信息
     *
     * @return Caffeine CacheStats
     */
    public CacheStats getStats() {
        return localCache.stats();
    }
}
