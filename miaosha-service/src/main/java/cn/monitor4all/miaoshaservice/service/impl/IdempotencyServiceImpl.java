package cn.monitor4all.miaoshaservice.service.impl;

import cn.monitor4all.miaoshaservice.service.IdempotencyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

/**
 * 幂等性保障服务实现
 * <p>
 * 基于 Redis SETNX 实现请求级幂等标记，配合 user+date 维度检查和数据库唯一索引形成三重保障。
 */
@Service
public class IdempotencyServiceImpl implements IdempotencyService {

    private static final Logger LOGGER = LoggerFactory.getLogger(IdempotencyServiceImpl.class);

    /** Redis key 前缀：幂等请求标记 */
    private static final String IDEMPOTENT_KEY_PREFIX = "idempotent:";

    /** Redis key 前缀：用户已购买标记 */
    private static final String USER_HAS_ORDER_KEY_PREFIX = "USER_HAS_ORDER_";

    /** 幂等标记有效期（分钟） */
    private static final long IDEMPOTENT_TTL_MINUTES = 5L;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * {@inheritDoc}
     * <p>
     * 使用 SETNX（SET if Not eXists）原子操作：
     * <ul>
     *     <li>key 不存在 → 设置成功 → 返回 true（首次请求）</li>
     *     <li>key 已存在 → 设置失败 → 返回 false（重复请求）</li>
     * </ul>
     */
    @Override
    public boolean checkAndMark(String requestId) {
        // 向后兼容：requestId 为空时跳过幂等检查
        if (requestId == null || requestId.isEmpty()) {
            LOGGER.debug("requestId 为空，跳过幂等检查");
            return true;
        }

        String key = IDEMPOTENT_KEY_PREFIX + requestId;
        Boolean setResult = stringRedisTemplate.opsForValue()
                .setIfAbsent(key, "1", IDEMPOTENT_TTL_MINUTES, TimeUnit.MINUTES);

        if (Boolean.TRUE.equals(setResult)) {
            LOGGER.info("幂等标记设置成功，requestId: {}", requestId);
            return true;
        }

        LOGGER.warn("检测到重复请求，requestId: {}", requestId);
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasUserPurchased(Long userId, String date) {
        if (userId == null || date == null || date.isEmpty()) {
            return false;
        }

        String key = USER_HAS_ORDER_KEY_PREFIX + date + "_" + userId;
        Boolean exists = stringRedisTemplate.hasKey(key);

        if (Boolean.TRUE.equals(exists)) {
            LOGGER.info("用户已购买该日期票券，userId: {}, date: {}", userId, date);
            return true;
        }
        return false;
    }

    /**
     * {@inheritDoc}
     * <p>
     * 仅在系统异常等非业务预期失败时调用，允许客户端安全重试。
     */
    @Override
    public void removeIdempotentMark(String requestId) {
        if (requestId == null || requestId.isEmpty()) {
            return;
        }

        String key = IDEMPOTENT_KEY_PREFIX + requestId;
        Boolean deleted = stringRedisTemplate.delete(key);

        if (Boolean.TRUE.equals(deleted)) {
            LOGGER.info("已移除幂等标记，允许重试，requestId: {}", requestId);
        } else {
            LOGGER.debug("幂等标记不存在或已过期，requestId: {}", requestId);
        }
    }
}
