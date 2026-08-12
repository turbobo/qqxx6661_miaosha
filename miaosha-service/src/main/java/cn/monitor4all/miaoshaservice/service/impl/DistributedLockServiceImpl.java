package cn.monitor4all.miaoshaservice.service.impl;

import cn.monitor4all.miaoshaservice.service.DistributedLockService;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

/**
 * 基于 Redisson 的分布式锁服务实现。
 * <p>
 * 核心特性：
 * <ul>
 *     <li>可重入锁（RLock）：同一线程可多次加锁而不会死锁</li>
 *     <li>Watchdog 自动续期：当 leaseTime &lt;= 0 时，Redisson 内置看门狗每 10s 自动续期（默认30s），
 *         避免因业务执行时间超过锁过期时间而导致锁提前释放</li>
 *     <li>安全释放：unlock 前校验当前线程是否持有锁，防止误释放他人的锁</li>
 * </ul>
 *
 * @author system
 */
@Service
public class DistributedLockServiceImpl implements DistributedLockService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DistributedLockServiceImpl.class);

    @Resource
    private RedissonClient redissonClient;

    /**
     * {@inheritDoc}
     * <p>
     * 当 leaseTime &lt;= 0 时不传递 leaseTime 参数，触发 Redisson Watchdog 自动续期机制。
     * </p>
     */
    @Override
    public boolean tryLock(String lockKey, long waitTime, long leaseTime, TimeUnit unit) {
        RLock lock = redissonClient.getLock(lockKey);
        try {
            boolean acquired;
            if (leaseTime <= 0) {
                // 不设置 leaseTime，启用 Watchdog 自动续期（默认30s，每10s续一次）
                acquired = lock.tryLock(waitTime, unit);
            } else {
                acquired = lock.tryLock(waitTime, leaseTime, unit);
            }
            if (acquired) {
                LOGGER.debug("成功获取分布式锁，key={}, waitTime={}, leaseTime={}", lockKey, waitTime, leaseTime);
            } else {
                LOGGER.warn("获取分布式锁超时，key={}, waitTime={}ms", lockKey, unit.toMillis(waitTime));
            }
            return acquired;
        } catch (InterruptedException e) {
            LOGGER.error("获取分布式锁被中断，key={}", lockKey, e);
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * 释放前通过 isHeldByCurrentThread() 校验，避免释放不属于自己的锁。
     * </p>
     */
    @Override
    public void unlock(String lockKey) {
        RLock lock = redissonClient.getLock(lockKey);
        try {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                LOGGER.debug("成功释放分布式锁，key={}", lockKey);
            } else {
                LOGGER.warn("尝试释放不属于自己的分布式锁，跳过释放，key={}", lockKey);
            }
        } catch (Exception e) {
            LOGGER.error("释放分布式锁异常，key={}", lockKey, e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isLocked(String lockKey) {
        RLock lock = redissonClient.getLock(lockKey);
        return lock.isLocked();
    }
}
