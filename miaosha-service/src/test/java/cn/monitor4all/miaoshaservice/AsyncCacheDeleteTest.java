package cn.monitor4all.miaoshaservice;

import cn.monitor4all.miaoshaservice.service.AsyncCacheDeleteService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 异步缓存删除服务测试类
 * 测试线程池和队列两种异步删除方式
 */
@SpringBootTest
@ActiveProfiles("test")
public class AsyncCacheDeleteTest {
    
    @Resource
    private AsyncCacheDeleteService asyncCacheDeleteService;
    
    /**
     * 测试线程池异步删除缓存
     */
    @Test
    public void testThreadPoolAsyncDelete() throws InterruptedException {
        String cacheKey = "test:threadpool:delete:key1";
        
        LOGGER.info("开始测试线程池异步删除缓存");
        
        // 提交异步删除任务
        asyncCacheDeleteService.deleteCacheAsync(cacheKey);
        
        // 等待一段时间让任务执行
        Thread.sleep(2000);
        
        LOGGER.info("线程池异步删除缓存测试完成");
    }
    
    /**
     * 测试线程池延迟删除缓存
     */
    @Test
    public void testThreadPoolDelayDelete() throws InterruptedException {
        String cacheKey = "test:threadpool:delay:delete:key2";
        long delayMillis = 3000; // 3秒延迟
        
        LOGGER.info("开始测试线程池延迟删除缓存，延迟: {}ms", delayMillis);
        
        // 提交延迟删除任务
        asyncCacheDeleteService.deleteCacheAsync(cacheKey, delayMillis);
        
        // 等待延迟时间 + 执行时间
        Thread.sleep(delayMillis + 2000);
        
        LOGGER.info("线程池延迟删除缓存测试完成");
    }
    
    /**
     * 测试队列异步删除缓存
     */
    @Test
    public void testQueueAsyncDelete() throws InterruptedException {
        String cacheKey = "test:queue:delete:key3";
        
        LOGGER.info("开始测试队列异步删除缓存");
        
        // 提交队列删除任务
        asyncCacheDeleteService.deleteCacheByQueue(cacheKey);
        
        // 等待一段时间让任务执行
        Thread.sleep(2000);
        
        LOGGER.info("队列异步删除缓存测试完成");
    }
    
    /**
     * 测试队列延迟删除缓存
     */
    @Test
    public void testQueueDelayDelete() throws InterruptedException {
        String cacheKey = "test:queue:delay:delete:key4";
        long delayMillis = 3000; // 3秒延迟
        
        LOGGER.info("开始测试队列延迟删除缓存，延迟: {}ms", delayMillis);
        
        // 提交延迟删除任务
        asyncCacheDeleteService.deleteCacheByQueue(cacheKey, delayMillis);
        
        // 等待延迟时间 + 执行时间
        Thread.sleep(delayMillis + 2000);
        
        LOGGER.info("队列延迟删除缓存测试完成");
    }
    
    /**
     * 测试双重异步删除缓存
     */
    @Test
    public void testDualAsyncDelete() throws InterruptedException {
        String cacheKey = "test:dual:delete:key5";
        
        LOGGER.info("开始测试双重异步删除缓存");
        
        // 提交双重异步删除任务
        asyncCacheDeleteService.deleteCacheDualAsync(cacheKey);
        
        // 等待一段时间让任务执行
        Thread.sleep(3000);
        
        LOGGER.info("双重异步删除缓存测试完成");
    }
    
    /**
     * 测试双重异步延迟删除缓存
     */
    @Test
    public void testDualAsyncDelayDelete() throws InterruptedException {
        String cacheKey = "test:dual:delay:delete:key6";
        long delayMillis = 3000; // 3秒延迟
        
        LOGGER.info("开始测试双重异步延迟删除缓存，延迟: {}ms", delayMillis);
        
        // 提交双重异步延迟删除任务
        asyncCacheDeleteService.deleteCacheDualAsync(cacheKey, delayMillis);
        
        // 等待延迟时间 + 执行时间
        Thread.sleep(delayMillis + 3000);
        
        LOGGER.info("双重异步延迟删除缓存测试完成");
    }
    
    /**
     * 测试批量异步删除缓存
     */
    @Test
    public void testBatchAsyncDelete() throws InterruptedException {
        List<String> cacheKeys = Arrays.asList(
            "test:batch:delete:key7",
            "test:batch:delete:key8",
            "test:batch:delete:key9"
        );
        
        LOGGER.info("开始测试批量异步删除缓存，数量: {}", cacheKeys.size());
        
        // 提交批量删除任务
        asyncCacheDeleteService.deleteCacheBatchAsync(cacheKeys);
        
        // 等待一段时间让任务执行
        Thread.sleep(5000);
        
        LOGGER.info("批量异步删除缓存测试完成");
    }
    
    /**
     * 测试高并发异步删除缓存
     */
    @Test
    public void testConcurrentAsyncDelete() throws InterruptedException {
        int threadCount = 10;
        CountDownLatch latch = new CountDownLatch(threadCount);
        
        LOGGER.info("开始测试高并发异步删除缓存，线程数: {}", threadCount);
        
        for (int i = 0; i < threadCount; i++) {
            final int threadIndex = i;
            new Thread(() -> {
                try {
                    String cacheKey = "test:concurrent:delete:key" + threadIndex;
                    LOGGER.debug("线程{}开始删除缓存: {}", threadIndex, cacheKey);
                    
                    // 使用双重异步删除
                    asyncCacheDeleteService.deleteCacheDualAsync(cacheKey);
                    
                    LOGGER.debug("线程{}删除缓存任务已提交: {}", threadIndex, cacheKey);
                } catch (Exception e) {
                    LOGGER.error("线程{}删除缓存失败", threadIndex, e);
                } finally {
                    latch.countDown();
                }
            }).start();
        }
        
        // 等待所有线程完成
        boolean completed = latch.await(10, TimeUnit.SECONDS);
        
        if (completed) {
            LOGGER.info("高并发异步删除缓存测试完成，所有线程执行完毕");
        } else {
            LOGGER.warn("高并发异步删除缓存测试超时，部分线程可能未完成");
        }
    }
    
    /**
     * 测试异常情况下的兜底删除
     */
    @Test
    public void testFallbackDelete() throws InterruptedException {
        String cacheKey = "test:fallback:delete:key10";
        
        LOGGER.info("开始测试异常情况下的兜底删除");
        
        try {
            // 模拟异常情况（传入null）
            asyncCacheDeleteService.deleteCacheAsync(null);
        } catch (Exception e) {
            LOGGER.info("捕获到预期异常: {}", e.getMessage());
        }
        
        // 等待一段时间
        Thread.sleep(2000);
        
        LOGGER.info("异常情况下的兜底删除测试完成");
    }
    
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(AsyncCacheDeleteTest.class);
}
