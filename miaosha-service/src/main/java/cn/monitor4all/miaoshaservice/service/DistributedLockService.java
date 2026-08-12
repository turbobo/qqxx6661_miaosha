package cn.monitor4all.miaoshaservice.service;

import java.util.concurrent.TimeUnit;

/**
 * 分布式锁服务接口。
 * <p>
 * 基于 Redisson 实现，提供可重入、自动续期（Watchdog）的分布式锁能力，
 * 替代原有基于 SETNX 的自研 RedisLock。
 * </p>
 *
 * @author system
 */
public interface DistributedLockService {

    /**
     * 尝试获取分布式锁。
     *
     * @param lockKey   锁的唯一标识（Redis Key）
     * @param waitTime  等待获取锁的最大时间，超时后返回 false
     * @param leaseTime 锁的自动释放时间；若值 &lt;= 0 则启用 Watchdog 自动续期（默认30s）
     * @param unit      时间单位
     * @return true 表示成功获取锁，false 表示获取超时或失败
     */
    boolean tryLock(String lockKey, long waitTime, long leaseTime, TimeUnit unit);

    /**
     * 释放分布式锁。
     * <p>
     * 仅当当前线程持有锁时才执行释放操作，防止误释放他人的锁。
     * </p>
     *
     * @param lockKey 锁的唯一标识（Redis Key）
     */
    void unlock(String lockKey);

    /**
     * 检查指定锁是否被持有。
     *
     * @param lockKey 锁的唯一标识（Redis Key）
     * @return true 表示锁已被持有，false 表示锁空闲
     */
    boolean isLocked(String lockKey);
}
