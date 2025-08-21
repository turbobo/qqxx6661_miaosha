package cn.monitor4all.miaoshaservice;

import cn.monitor4all.miaoshaservice.service.AsyncCacheDeleteService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.annotation.Resource;

/**
 * 缓存删除队列测试类
 * 测试延迟删除队列的消息接收和处理
 */
@SpringBootTest
@ActiveProfiles("test")
public class CacheDeleteQueueTest {
    
    @Resource
    private AsyncCacheDeleteService asyncCacheDeleteService;
    
    /**
     * 测试立即删除队列
     */
    @Test
    public void testImmediateDeleteQueue() throws InterruptedException {
        String cacheKey = "test:immediate:delete:key1";
        
        LOGGER.info("开始测试立即删除队列");
        
        // 发送立即删除消息
        asyncCacheDeleteService.deleteCacheByQueue(cacheKey);
        
        // 等待消息处理
        Thread.sleep(3000);
        
        LOGGER.info("立即删除队列测试完成");
    }
    
    /**
     * 测试延迟删除队列
     */
    @Test
    public void testDelayDeleteQueue() throws InterruptedException {
        String cacheKey = "test:delay:delete:key2";
        long delayMillis = 2000; // 2秒延迟
        
        LOGGER.info("开始测试延迟删除队列，延迟: {}ms", delayMillis);
        
        // 发送延迟删除消息
        asyncCacheDeleteService.deleteCacheByQueue(cacheKey, delayMillis);
        
        // 等待延迟时间 + 处理时间
        Thread.sleep(delayMillis + 3000);
        
        LOGGER.info("延迟删除队列测试完成");
    }
    
    /**
     * 测试双重异步删除（包含队列）
     */
    @Test
    public void testDualAsyncDeleteWithQueue() throws InterruptedException {
        String cacheKey = "test:dual:queue:delete:key3";
        long delayMillis = 1500; // 1.5秒延迟
        
        LOGGER.info("开始测试双重异步删除（包含队列），延迟: {}ms", delayMillis);
        
        // 使用双重异步删除
        asyncCacheDeleteService.deleteCacheDualAsync(cacheKey, delayMillis);
        
        // 等待延迟时间 + 处理时间
        Thread.sleep(delayMillis + 4000);
        
        LOGGER.info("双重异步删除（包含队列）测试完成");
    }
    
    /**
     * 测试批量队列删除
     */
    @Test
    public void testBatchQueueDelete() throws InterruptedException {
        String[] cacheKeys = {
            "test:batch:queue:delete:key4",
            "test:batch:queue:delete:key5",
            "test:batch:queue:delete:key6"
        };
        
        LOGGER.info("开始测试批量队列删除，数量: {}", cacheKeys.length);
        
        for (String cacheKey : cacheKeys) {
            // 每个缓存键使用队列删除
            asyncCacheDeleteService.deleteCacheByQueue(cacheKey);
            LOGGER.debug("已发送删除消息: {}", cacheKey);
        }
        
        // 等待消息处理
        Thread.sleep(5000);
        
        LOGGER.info("批量队列删除测试完成");
    }
    
    /**
     * 测试延迟队列的准确性
     */
    @Test
    public void testDelayQueueAccuracy() throws InterruptedException {
        String cacheKey = "test:delay:accuracy:key7";
        long delayMillis = 1000; // 1秒延迟
        
        LOGGER.info("开始测试延迟队列的准确性，延迟: {}ms", delayMillis);
        
        long startTime = System.currentTimeMillis();
        
        // 发送延迟删除消息
        asyncCacheDeleteService.deleteCacheByQueue(cacheKey, delayMillis);
        
        LOGGER.info("延迟删除消息已发送，开始等待...");
        
        // 等待延迟时间 + 处理时间
        Thread.sleep(delayMillis + 2000);
        
        long endTime = System.currentTimeMillis();
        long actualDelay = endTime - startTime;
        
        LOGGER.info("延迟队列测试完成，实际延迟: {}ms，预期延迟: {}ms", actualDelay, delayMillis);
        
        // 验证延迟时间是否合理（允许500ms误差）
        assert Math.abs(actualDelay - delayMillis) <= 500 : 
            String.format("延迟时间不准确，实际: %dms，预期: %dms", actualDelay, delayMillis);
    }
    
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(CacheDeleteQueueTest.class);
}
