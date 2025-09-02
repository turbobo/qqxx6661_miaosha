package cn.monitor4all.miaoshaservice.utils.redis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Random;
import java.util.concurrent.TimeUnit;

public class RedisLock {

    /**
     * 默认请求锁的超时时间(ms 毫秒)
     */
    private static final long TIME_OUT = 100;

    /**
     * 默认锁的有效时间(s)
     */
    public static final int EXPIRE = 60;

    private static Logger logger = LoggerFactory.getLogger(RedisLock.class);

    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 锁标志对应的key
     */
    private String lockKey;
    /**
     * 锁的有效时间(s)
     */
    private int expireTime = EXPIRE;

    /**
     * 请求锁的超时时间(ms)
     */
    private long timeOut = TIME_OUT;

    /**
     * 锁的有效时间
     */
    private long expires = 0;

    /**
     * 锁标记
     */
    private volatile boolean locked = false;

    private final Random random = new Random();

    /**
     * 使用默认的锁过期时间和请求锁的超时时间
     *
     * @param redisTemplate
     * @param lockKey       锁的key（Redis的Key）
     */
    public RedisLock(RedisTemplate<String, Object> redisTemplate, String lockKey) {
        this.redisTemplate = redisTemplate;
        this.lockKey = lockKey + "_lock";
    }

    /**
     * 使用默认的请求锁的超时时间，指定锁的过期时间
     *
     * @param redisTemplate
     * @param lockKey       锁的key（Redis的Key）
     * @param expireTime    锁的过期时间(单位：秒)
     */
    public RedisLock(RedisTemplate<String, Object> redisTemplate, String lockKey, int expireTime) {
        this(redisTemplate, lockKey);
        this.expireTime = expireTime;
    }

    /**
     * 使用默认的锁的过期时间，指定请求锁的超时时间
     *
     * @param redisTemplate
     * @param lockKey       锁的key（Redis的Key）
     * @param timeOut       请求锁的超时时间(单位：毫秒)
     */
    public RedisLock(RedisTemplate<String, Object> redisTemplate, String lockKey, long timeOut) {
        this(redisTemplate, lockKey);
        this.timeOut = timeOut;
    }

    /**
     * 锁的过期时间和请求锁的超时时间都是用指定的值
     *
     * @param redisTemplate
     * @param lockKey       锁的key（Redis的Key）
     * @param expireTime    锁的过期时间(单位：秒)
     * @param timeOut       请求锁的超时时间(单位：毫秒)
     */
    public RedisLock(RedisTemplate<String, Object> redisTemplate, String lockKey, int expireTime, long timeOut) {
        this(redisTemplate, lockKey, expireTime);
        this.timeOut = timeOut;
    }

    /**
     * @return 获取锁的key
     */
    public String getLockKey() {
        return lockKey;
    }

    /**
     * 获得 lock.
     * 实现思路: 主要是使用了redis 的setnx命令,缓存了锁.
     * redis 缓存的 key 是锁的 key,所有的共享, value 是锁的到期时间(注意:这里把过期时间放在value了,没有时间上设置其超时时间)
     * 执行过程:
     * 1.通过setnx尝试设置某个key的值,成功(当前没有这个锁)则返回,成功获得锁
     * 2.锁已经存在则获取锁的到期时间,和当前时间比较,超时的话,则设置新的值
     *
     * @return true if lock is acquired, false acquire timeouted
     * @throws InterruptedException in case of thread interruption
     */
    public boolean lock() {
        // 请求锁超时时间，纳秒  （1000000纳秒 = 1毫秒）
        long timeout = timeOut * 1000000;
        // 系统当前时间，纳秒
        long nowTime = System.nanoTime();

        while ((System.nanoTime() - nowTime) < timeout) {
            // 分布式服务器有时差，这里给1秒的误差值
            expires = System.currentTimeMillis() + (expireTime + 1) * 1000;
            // 设置锁的有效期，也是锁的自动释放时间，也是一个客户端在其他客户端能抢占锁之前可以执行任务的时间
            // 可以防止因异常情况无法释放锁而造成死锁情况的发生
            Boolean flag = redisTemplate.opsForValue().setIfAbsent(lockKey, String.valueOf(expires), expireTime, TimeUnit.SECONDS);
            if (flag != null && flag) {
                locked = true;
                // 上锁成功结束请求
                return true;
            }

//            Object currentValueStr = redisTemplate.opsForValue().get(lockKey); //redis里的时间
//            if (currentValueStr != null && Long.parseLong(currentValueStr.toString()) < System.currentTimeMillis()) {
//                //判断是否为空，不为空的情况下，如果被其他线程设置了值，则第二个条件判断是过不去的
//                // lock is expired
//
//                Long oldValueStr = Long.valueOf((String) redisTemplate.opsForValue().getAndSet(lockKey,  String.valueOf(expires)));
//                //获取上一个锁到期时间，并设置现在的锁到期时间，
//                //只有一个线程才能获取上一个线上的设置时间，因为jedis.getSet是同步的
//                if (oldValueStr != null && oldValueStr.equals(currentValueStr)) {
//                    //防止误删（覆盖，因为key是相同的）了他人的锁——这里达不到效果，这里值会被覆盖，但是因为什么相差了很少的时间，所以可以接受
//
//                    //[分布式的情况下]:如果这个时候，多个线程恰好都到了这里，但是只有一个线程的设置值和当前值相同，他才有权利获取锁
//                    // lock acquired
//                    locked = true;
//                    return true;
//                }
//            }

            /*
                延迟10 毫秒,  这里使用随机时间可能会好一点,可以防止饥饿进程的出现,即,当同时到达多个进程,
                只会有一个进程获得锁,其他的都用同样的频率进行尝试,后面有来了一些进行,也以同样的频率申请锁,这将可能导致前面来的锁得不到满足.
                使用随机的等待时间可以一定程度上保证公平性
             */
            try {
                Thread.sleep(10, random.nextInt(50000));
            } catch (InterruptedException e) {
                logger.error("获取分布式锁休眠被中断：", e);
                Thread.currentThread().interrupt();
            }

        }
        return locked;
    }

    /**
     * 根据自定义的固定的线程休眠时间获取分布式锁（还可以使用指数退避算法来逐渐增加重试间隔，感觉业务上面不需要设置的这么复杂，暂未提供具体实现）
     *
     * @param millis 线程睡眠毫秒时间
     * */
    public boolean lockWithCustomSleepTime(long millis) {
        // 请求锁超时时间，纳秒  （1000000纳秒 = 1毫秒）
        long timeout = timeOut * 1000000;
        // 系统当前时间，纳秒
        long nowTime = System.nanoTime();

        while ((System.nanoTime() - nowTime) < timeout) {
            expires = System.currentTimeMillis() + (expireTime + 1) * 1000;
            Boolean flag = redisTemplate.opsForValue().setIfAbsent(lockKey, String.valueOf(expires), expireTime, TimeUnit.SECONDS);
            if (flag != null && flag) {
                locked = true;
                return true;
            }

            try {
                Thread.sleep(millis, random.nextInt(50000));
            } catch (InterruptedException e) {
                logger.error("获取分布式锁休眠被中断：", e);
                Thread.currentThread().interrupt();
            }

        }

        return locked;
    }

    public boolean lockWithoutRetry() {
        // 分布式服务器有时差，这里给1秒的误差值
        expires = System.currentTimeMillis() + (expireTime + 1) * 1000;
        // 设置锁的有效期，也是锁的自动释放时间，也是一个客户端在其他客户端能抢占锁之前可以执行任务的时间
        // 可以防止因异常情况无法释放锁而造成死锁情况的发生
        Boolean flag = redisTemplate.opsForValue().setIfAbsent(lockKey, String.valueOf(expires), expireTime, TimeUnit.SECONDS);
        if (flag != null && flag) {
            locked = true;
            // 上锁成功结束请求
            return true;
        }

        return locked;
    }

    /**
     * 解锁
     */
    public synchronized void unlock() {
        // 只有加锁成功并且锁还有效才去释放锁
        if (locked && expires > System.currentTimeMillis()) {
            redisTemplate.delete(lockKey);
            locked = false;
        }
    }
}