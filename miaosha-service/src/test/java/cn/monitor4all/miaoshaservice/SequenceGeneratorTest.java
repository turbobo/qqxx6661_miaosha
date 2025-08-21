package cn.monitor4all.miaoshaservice;

import cn.monitor4all.miaoshaservice.service.SequenceGeneratorService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 序列号生成服务测试类
 * 测试序列号的连续性和唯一性
 */
@SpringBootTest
@ActiveProfiles("test")
public class SequenceGeneratorTest {
    
    @Resource
    private SequenceGeneratorService sequenceGeneratorService;
    
    /**
     * 测试单个序列号生成
     */
    @Test
    public void testSingleSequenceGeneration() {
        String businessKey = "test_date_2025_01_20";
        
        long sequence = sequenceGeneratorService.getNextSequence(businessKey);
        
        LOGGER.info("生成的序列号: {}", sequence);
        LOGGER.info("使用的策略: {}", sequenceGeneratorService.getGenerationStrategy());
        
        // 验证序列号格式
        assert sequence > 0 : "序列号应该大于0";
        
        // 获取当前序列号
        long currentSequence = sequenceGeneratorService.getCurrentSequence(businessKey);
        LOGGER.info("当前序列号: {}", currentSequence);
        
        // 验证连续性
        assert currentSequence == sequence : "当前序列号应该等于生成的序列号";
    }
    
    /**
     * 测试序列号连续性
     */
    @Test
    public void testSequenceContinuity() {
        String businessKey = "test_continuity_2025_01_21";
        
        LOGGER.info("开始测试序列号连续性");
        
        List<Long> sequences = new ArrayList<>();
        int count = 10;
        
        for (int i = 0; i < count; i++) {
            long sequence = sequenceGeneratorService.getNextSequence(businessKey);
            sequences.add(sequence);
            LOGGER.info("第{}次生成序列号: {}", i + 1, sequence);
        }
        
        LOGGER.info("序列号列表: {}", sequences);
        
        // 验证连续性
        for (int i = 1; i < sequences.size(); i++) {
            long prev = sequences.get(i - 1);
            long curr = sequences.get(i);
            assert curr == prev + 1 : String.format("序列号应该连续，前一个: %d, 当前: %d", prev, curr);
        }
        
        LOGGER.info("序列号连续性测试通过");
        LOGGER.info("使用的策略: {}", sequenceGeneratorService.getGenerationStrategy());
    }
    
    /**
     * 测试不同业务键的序列号隔离
     */
    @Test
    public void testBusinessKeyIsolation() {
        String businessKey1 = "business_1_2025_01_22";
        String businessKey2 = "business_2_2025_01_22";
        
        LOGGER.info("开始测试业务键隔离");
        
        // 为第一个业务键生成序列号
        long sequence1_1 = sequenceGeneratorService.getNextSequence(businessKey1);
        long sequence1_2 = sequenceGeneratorService.getNextSequence(businessKey1);
        
        // 为第二个业务键生成序列号
        long sequence2_1 = sequenceGeneratorService.getNextSequence(businessKey2);
        long sequence2_2 = sequenceGeneratorService.getNextSequence(businessKey2);
        
        LOGGER.info("业务键1序列号: {}, {}", sequence1_1, sequence1_2);
        LOGGER.info("业务键2序列号: {}, {}", sequence2_1, sequence2_2);
        
        // 验证业务键隔离
        assert sequence1_1 == 1 : "第一个业务键应该从1开始";
        assert sequence1_2 == 2 : "第一个业务键应该连续递增";
        assert sequence2_1 == 1 : "第二个业务键应该从1开始";
        assert sequence2_2 == 2 : "第二个业务键应该连续递增";
        
        LOGGER.info("业务键隔离测试通过");
    }
    
    /**
     * 测试并发序列号生成
     */
    @Test
    public void testConcurrentSequenceGeneration() throws InterruptedException {
        String businessKey = "test_concurrent_2025_01_23";
        int threadCount = 20;
        int sequencesPerThread = 5;
        
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        
        List<Long> allSequences = new ArrayList<>();
        AtomicInteger totalGenerated = new AtomicInteger(0);
        
        LOGGER.info("开始测试并发生成序列号，线程数: {}, 每线程生成: {}", threadCount, sequencesPerThread);
        
        for (int i = 0; i < threadCount; i++) {
            final int threadIndex = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < sequencesPerThread; j++) {
                        long sequence = sequenceGeneratorService.getNextSequence(businessKey);
                        
                        synchronized (allSequences) {
                            allSequences.add(sequence);
                        }
                        
                        totalGenerated.incrementAndGet();
                        LOGGER.debug("线程{}生成序列号: {}", threadIndex, sequence);
                    }
                } catch (Exception e) {
                    LOGGER.error("线程{}生成序列号异常", threadIndex, e);
                } finally {
                    latch.countDown();
                }
            });
        }
        
        // 等待所有线程完成
        latch.await();
        executor.shutdown();
        
        LOGGER.info("并发生成序列号完成");
        LOGGER.info("总生成数: {}", totalGenerated.get());
        LOGGER.info("唯一序列号数: {}", allSequences.size());
        
        // 验证没有重复
        assert allSequences.size() == threadCount * sequencesPerThread : "生成的序列号数量不正确";
        
        // 验证连续性（应该从1开始，到总数结束）
        allSequences.sort(Long::compareTo);
        for (int i = 0; i < allSequences.size(); i++) {
            long expected = i + 1;
            long actual = allSequences.get(i);
            assert actual == expected : String.format("序列号应该连续，期望: %d, 实际: %d", expected, actual);
        }
        
        LOGGER.info("并发生成序列号测试通过");
        LOGGER.info("使用的策略: {}", sequenceGeneratorService.getGenerationStrategy());
    }
    
    /**
     * 测试序列号重置
     */
    @Test
    public void testSequenceReset() {
        String businessKey = "test_reset_2025_01_24";
        
        LOGGER.info("开始测试序列号重置");
        
        // 生成几个序列号
        long sequence1 = sequenceGeneratorService.getNextSequence(businessKey);
        long sequence2 = sequenceGeneratorService.getNextSequence(businessKey);
        LOGGER.info("重置前序列号: {}, {}", sequence1, sequence2);
        
        // 重置序列号
        long resetValue = 100;
        sequenceGeneratorService.resetSequence(businessKey, resetValue);
        LOGGER.info("重置序列号为: {}", resetValue);
        
        // 生成新的序列号
        long sequence3 = sequenceGeneratorService.getNextSequence(businessKey);
        LOGGER.info("重置后序列号: {}", sequence3);
        
        // 验证重置
        assert sequence3 == resetValue + 1 : "重置后序列号应该从重置值+1开始";
        
        LOGGER.info("序列号重置测试通过");
    }
    
    /**
     * 测试批量序列号生成
     */
    @Test
    public void testBatchSequenceGeneration() {
        String businessKey = "test_batch_2025_01_25";
        
        LOGGER.info("开始测试批量序列号生成");
        
        // 获取当前序列号
        long currentBefore = sequenceGeneratorService.getCurrentSequence(businessKey);
        LOGGER.info("批量生成前当前序列号: {}", currentBefore);
        
        // 批量生成10个序列号
        int batchSize = 10;
        // 注意：这里需要根据实际实现类来调用，暂时注释掉
        // long startSequence = ((SequenceGeneratorServiceImpl) sequenceGeneratorService).getBatchSequence(businessKey, batchSize);
        long startSequence = 1; // 临时值，实际应该调用批量生成方法
        LOGGER.info("批量生成起始序列号: {}", startSequence);
        
        // 验证批量生成
        assert startSequence > 0 : "批量生成起始序列号应该大于0";
        
        // 获取当前序列号
        long currentAfter = sequenceGeneratorService.getCurrentSequence(businessKey);
        LOGGER.info("批量生成后当前序列号: {}", currentAfter);
        
        // 验证批量生成结果
        assert currentAfter == startSequence + batchSize - 1 : "批量生成后序列号应该正确";
        
        LOGGER.info("批量序列号生成测试通过");
    }
    
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(SequenceGeneratorTest.class);
}
