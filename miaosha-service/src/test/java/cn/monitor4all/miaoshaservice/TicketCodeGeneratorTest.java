package cn.monitor4all.miaoshaservice;

import cn.monitor4all.miaoshaservice.service.TicketCodeGeneratorService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.annotation.Resource;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 票券编码生成服务测试类
 * 测试编码的唯一性和各种生成策略
 */
@SpringBootTest
@ActiveProfiles("test")
public class TicketCodeGeneratorTest {
    
    @Resource
    private TicketCodeGeneratorService ticketCodeGeneratorService;
    
    /**
     * 测试单个编码生成
     */
    @Test
    public void testSingleCodeGeneration() {
        Long userId = 1001L;
        String date = "2025-01-20";
        
        String ticketCode = ticketCodeGeneratorService.generateUniqueTicketCode(userId, date);
        
        LOGGER.info("生成的票券编码: {}", ticketCode);
        LOGGER.info("使用的策略: {}", ticketCodeGeneratorService.getGenerationStrategy());
        
        // 验证编码格式
        assert ticketCode.startsWith("T");
        assert ticketCode.length() >= 20;
        
        // 验证唯一性
        boolean isUnique = ticketCodeGeneratorService.isTicketCodeUnique(ticketCode);
        LOGGER.info("编码唯一性验证: {}", isUnique);
    }
    
    /**
     * 测试批量编码生成（验证唯一性）
     */
    @Test
    public void testBatchCodeGeneration() {
        Long userId = 1002L;
        String date = "2025-01-21";
        int count = 100;
        
        Set<String> codes = new HashSet<>();
        Set<String> duplicates = new HashSet<>();
        
        LOGGER.info("开始生成{}个票券编码", count);
        
        for (int i = 0; i < count; i++) {
            String ticketCode = ticketCodeGeneratorService.generateUniqueTicketCode(userId, date);
            
            if (codes.contains(ticketCode)) {
                duplicates.add(ticketCode);
                LOGGER.warn("发现重复编码: {}", ticketCode);
            } else {
                codes.add(ticketCode);
            }
            
            if (i % 10 == 0) {
                LOGGER.info("已生成{}个编码", i + 1);
            }
        }
        
        LOGGER.info("编码生成完成，总数: {}, 重复数: {}", codes.size(), duplicates.size());
        LOGGER.info("使用的策略: {}", ticketCodeGeneratorService.getGenerationStrategy());
        
        // 验证没有重复
        assert duplicates.isEmpty() : "存在重复的票券编码: " + duplicates;
        assert codes.size() == count : "生成的编码数量不正确";
    }
    
    /**
     * 测试并发编码生成（验证高并发下的唯一性）
     */
    @Test
    public void testConcurrentCodeGeneration() throws InterruptedException {
        Long userId = 1003L;
        String date = "2025-01-22";
        int threadCount = 50;
        int codesPerThread = 10;
        
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        
        Set<String> allCodes = new HashSet<>();
        AtomicInteger totalGenerated = new AtomicInteger(0);
        AtomicInteger duplicateCount = new AtomicInteger(0);
        
        LOGGER.info("开始并发生成票券编码，线程数: {}, 每线程生成: {}", threadCount, codesPerThread);
        
        for (int i = 0; i < threadCount; i++) {
            final int threadIndex = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < codesPerThread; j++) {
                        String ticketCode = ticketCodeGeneratorService.generateUniqueTicketCode(userId, date);
                        
                        synchronized (allCodes) {
                            if (allCodes.contains(ticketCode)) {
                                duplicateCount.incrementAndGet();
                                LOGGER.warn("线程{}发现重复编码: {}", threadIndex, ticketCode);
                            } else {
                                allCodes.add(ticketCode);
                            }
                        }
                        
                        totalGenerated.incrementAndGet();
                    }
                } catch (Exception e) {
                    LOGGER.error("线程{}生成编码异常", threadIndex, e);
                } finally {
                    latch.countDown();
                }
            });
        }
        
        // 等待所有线程完成
        latch.await();
        executor.shutdown();
        
        LOGGER.info("并发编码生成完成");
        LOGGER.info("总生成数: {}", totalGenerated.get());
        LOGGER.info("唯一编码数: {}", allCodes.size());
        LOGGER.info("重复编码数: {}", duplicateCount.get());
        LOGGER.info("使用的策略: {}", ticketCodeGeneratorService.getGenerationStrategy());
        
        // 验证没有重复
        assert duplicateCount.get() == 0 : "并发生成中存在重复编码";
        assert allCodes.size() == threadCount * codesPerThread : "生成的编码数量不正确";
    }
    
    /**
     * 测试不同用户和日期的编码生成
     */
    @Test
    public void testDifferentUsersAndDates() {
        Long[] userIds = {1001L, 2001L, 3001L, 4001L, 5001L};
        String[] dates = {"2025-01-20", "2025-01-21", "2025-01-22"};
        
        Set<String> allCodes = new HashSet<>();
        
        LOGGER.info("开始测试不同用户和日期的编码生成");
        
        for (Long userId : userIds) {
            for (String date : dates) {
                String ticketCode = ticketCodeGeneratorService.generateUniqueTicketCode(userId, date);
                allCodes.add(ticketCode);
                
                LOGGER.info("用户: {}, 日期: {}, 编码: {}", userId, date, ticketCode);
            }
        }
        
        LOGGER.info("不同用户和日期编码生成完成，总数: {}", allCodes.size());
        LOGGER.info("使用的策略: {}", ticketCodeGeneratorService.getGenerationStrategy());
        
        // 验证所有编码都是唯一的
        assert allCodes.size() == userIds.length * dates.length : "编码数量不正确";
    }
    
    /**
     * 测试编码唯一性验证
     */
    @Test
    public void testCodeUniquenessValidation() {
        Long userId = 1004L;
        String date = "2025-01-23";
        
        // 生成第一个编码
        String firstCode = ticketCodeGeneratorService.generateUniqueTicketCode(userId, date);
        LOGGER.info("第一个编码: {}", firstCode);
        
        // 验证第一个编码是唯一的
        boolean isFirstUnique = ticketCodeGeneratorService.isTicketCodeUnique(firstCode);
        LOGGER.info("第一个编码唯一性: {}", isFirstUnique);
        
        // 生成第二个编码
        String secondCode = ticketCodeGeneratorService.generateUniqueTicketCode(userId, date);
        LOGGER.info("第二个编码: {}", secondCode);
        
        // 验证两个编码不同
        assert !firstCode.equals(secondCode) : "生成的编码应该不同";
        
        LOGGER.info("编码唯一性验证通过");
    }
    
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(TicketCodeGeneratorTest.class);
}
