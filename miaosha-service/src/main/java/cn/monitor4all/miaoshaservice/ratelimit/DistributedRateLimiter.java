package cn.monitor4all.miaoshaservice.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.Collections;

/**
 * 分布式令牌桶限流器
 * <p>
 * 基于 Redis Lua 脚本实现分布式令牌桶算法，支持全局接口级和用户级两个维度的限流。
 * 当 Redis 不可用时自动降级为允许通过，保证服务可用性。
 * </p>
 *
 * @author monitor4all
 */
@Component
public class DistributedRateLimiter {

    private static final Logger logger = LoggerFactory.getLogger(DistributedRateLimiter.class);

    /** 全局接口限流 key 前缀 */
    private static final String GLOBAL_RATE_LIMIT_KEY_PREFIX = "rate_limit:api:";

    /** 用户级限流 key 前缀 */
    private static final String USER_RATE_LIMIT_KEY_PREFIX = "rate_limit:user:";

    /** 每次请求消耗令牌数 */
    private static final int REQUESTED_TOKENS = 1;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private DefaultRedisScript<Long> rateLimitScript;

    /** 全局购票接口桶容量 */
    @Value("${ratelimit.global.purchase.max-tokens:5000}")
    private int globalMaxTokens;

    /** 全局购票接口每秒补充令牌数 */
    @Value("${ratelimit.global.purchase.refill-rate:5000}")
    private int globalRefillRate;

    /** 用户级桶容量 */
    @Value("${ratelimit.user.max-tokens:5}")
    private int userMaxTokens;

    /** 用户级每秒补充令牌数 */
    @Value("${ratelimit.user.refill-rate:5}")
    private int userRefillRate;

    /**
     * 初始化 Lua 脚本
     * 在 Bean 初始化后加载 Lua 脚本到 RedisScript 对象中，避免每次调用时重复加载。
     */
    @PostConstruct
    public void init() {
        rateLimitScript = new DefaultRedisScript<>();
        rateLimitScript.setLocation(new org.springframework.core.io.ClassPathResource("lua/rate_limit.lua"));
        rateLimitScript.setResultType(Long.class);
        logger.info("分布式限流 Lua 脚本加载完成，全局限流参数: maxTokens={}, refillRate={}, 用户限流参数: maxTokens={}, refillRate={}",
                globalMaxTokens, globalRefillRate, userMaxTokens, userRefillRate);
    }

    /**
     * 全局接口限流
     * <p>
     * 基于接口路径作为限流维度，保护后端服务不被过载。
     * </p>
     *
     * @param apiPath    接口路径，如 "/api/tickets/v3/purchase"
     * @param maxTokens  桶容量
     * @param refillRate 每秒补充令牌数
     * @return true=允许通过, false=限流拒绝
     */
    public boolean tryAcquire(String apiPath, int maxTokens, int refillRate) {
        String key = GLOBAL_RATE_LIMIT_KEY_PREFIX + apiPath;
        return executeRateLimitScript(key, maxTokens, refillRate);
    }

    /**
     * 用户级限流
     * <p>
     * 基于用户ID作为限流维度，防止单个用户恶意刷接口。
     * </p>
     *
     * @param userId     用户ID
     * @param maxTokens  桶容量（如5）
     * @param refillRate 每秒补充令牌数（如5）
     * @return true=允许通过, false=限流拒绝
     */
    public boolean tryAcquireForUser(Long userId, int maxTokens, int refillRate) {
        String key = USER_RATE_LIMIT_KEY_PREFIX + userId;
        return executeRateLimitScript(key, maxTokens, refillRate);
    }

    /**
     * 组合检查：先全局限流，再用户限流
     * <p>
     * 两层限流均通过才允许请求继续。全局限流保护整体服务，用户限流防止单点滥用。
     * 任一层限流被触发或 Redis 异常时，均按降级策略处理。
     * </p>
     *
     * @param apiPath 接口路径
     * @param userId  用户ID
     * @return true=允许通过, false=被限流
     */
    public boolean isAllowed(String apiPath, Long userId) {
        // 全局接口限流检查
        if (!tryAcquire(apiPath, globalMaxTokens, globalRefillRate)) {
            logger.warn("全局接口限流触发，apiPath={}, userId={}", apiPath, userId);
            return false;
        }
        // 用户级限流检查
        if (!tryAcquireForUser(userId, userMaxTokens, userRefillRate)) {
            logger.warn("用户级限流触发，apiPath={}, userId={}", apiPath, userId);
            return false;
        }
        return true;
    }

    /**
     * 执行 Redis Lua 限流脚本
     * <p>
     * 核心执行逻辑，异常时降级为允许通过（保证 Redis 故障不影响业务可用性）。
     * </p>
     *
     * @param key        Redis key
     * @param maxTokens  桶容量
     * @param refillRate 每秒补充令牌数
     * @return true=允许通过, false=限流拒绝; Redis异常时返回true
     */
    private boolean executeRateLimitScript(String key, int maxTokens, int refillRate) {
        try {
            Long result = stringRedisTemplate.execute(
                    rateLimitScript,
                    Collections.singletonList(key),
                    String.valueOf(maxTokens),
                    String.valueOf(refillRate),
                    String.valueOf(System.currentTimeMillis()),
                    String.valueOf(REQUESTED_TOKENS)
            );
            return result != null && result == 1L;
        } catch (Exception e) {
            logger.warn("Redis限流脚本执行异常，降级允许通过，key={}, error={}", key, e.getMessage());
            return true;
        }
    }
}
