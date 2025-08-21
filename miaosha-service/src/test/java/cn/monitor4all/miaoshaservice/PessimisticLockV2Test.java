package cn.monitor4all.miaoshaservice;

import cn.monitor4all.miaoshadao.model.ApiResponse;
import cn.monitor4all.miaoshadao.model.PurchaseRecord;
import cn.monitor4all.miaoshadao.model.PurchaseRequest;
import cn.monitor4all.miaoshaservice.service.TicketService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.annotation.Resource;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 悲观锁购票V2测试类
 * 测试使用SELECT FOR UPDATE锁住票券记录，事务控制，扣库存，生成ticket_order
 */
@SpringBootTest
@ActiveProfiles("test")
public class PessimisticLockV2Test {
    
    @Resource
    private TicketService ticketService;
    
    /**
     * 测试单个用户购票
     */
    @Test
    public void testSingleUserPurchase() {
        try {
            PurchaseRequest request = new PurchaseRequest();
            request.setUserId(1001L);
            request.setDate("2025-01-20");
            
            LOGGER.info("开始测试单个用户购票，用户ID: {}, 日期: {}", request.getUserId(), request.getDate());
            
            ApiResponse<PurchaseRecord> response = ticketService.purchaseTicketWithPessimisticLockV2(request);
            
            if (response.getCode() == 200) {
                PurchaseRecord record = response.getData();
                LOGGER.info("购票成功！用户ID: {}, 票券编码: {}, 购票日期: {}", 
                    record.getUserId(), record.getTicketCode(), record.getDate());
            } else {
                LOGGER.warn("购票失败：{}", response.getMessage());
            }
            
        } catch (Exception e) {
            LOGGER.error("测试异常", e);
        }
    }
    
    /**
     * 测试并发购票（悲观锁应该能正确处理并发）
     */
    @Test
    public void testConcurrentPurchase() throws InterruptedException {
        int threadCount = 10;
        String purchaseDate = "2025-01-20";
        
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        
        LOGGER.info("开始测试并发购票，线程数: {}, 购票日期: {}", threadCount, purchaseDate);
        
        for (int i = 0; i < threadCount; i++) {
            final int threadIndex = i;
            executor.submit(() -> {
                try {
                    PurchaseRequest request = new PurchaseRequest();
                    request.setUserId(2000L + threadIndex); // 不同用户ID
                    request.setDate(purchaseDate);
                    
                    LOGGER.info("线程{}开始购票，用户ID: {}", threadIndex, request.getUserId());
                    
                    ApiResponse<PurchaseRecord> response = ticketService.purchaseTicketWithPessimisticLockV2(request);
                    
                    if (response.getCode() == 200) {
                        PurchaseRecord record = response.getData();
                        LOGGER.info("线程{}购票成功！用户ID: {}, 票券编码: {}", 
                            threadIndex, record.getUserId(), record.getTicketCode());
                        successCount.incrementAndGet();
                    } else {
                        LOGGER.warn("线程{}购票失败：{}", threadIndex, response.getMessage());
                        failCount.incrementAndGet();
                    }
                    
                } catch (Exception e) {
                    LOGGER.error("线程{}购票异常", threadIndex, e);
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        // 等待所有线程完成
        latch.await();
        executor.shutdown();
        
        LOGGER.info("并发购票测试完成，成功: {}, 失败: {}", successCount.get(), failCount.get());
    }
    
    /**
     * 测试重复购票（同一用户同一日期）
     */
    @Test
    public void testDuplicatePurchase() {
        try {
            PurchaseRequest request = new PurchaseRequest();
            request.setUserId(3001L);
            request.setDate("2025-01-21");
            
            LOGGER.info("开始测试重复购票，用户ID: {}, 日期: {}", request.getUserId(), request.getDate());
            
            // 第一次购票
            ApiResponse<PurchaseRecord> response1 = ticketService.purchaseTicketWithPessimisticLockV2(request);
            if (response1.getCode() == 200) {
                LOGGER.info("第一次购票成功！票券编码: {}", response1.getData().getTicketCode());
            } else {
                LOGGER.warn("第一次购票失败：{}", response1.getMessage());
                return;
            }
            
            // 第二次购票（应该失败）
            ApiResponse<PurchaseRecord> response2 = ticketService.purchaseTicketWithPessimisticLockV2(request);
            if (response2.getCode() == 200) {
                LOGGER.warn("重复购票应该失败，但成功了！票券编码: {}", response2.getData().getTicketCode());
            } else {
                LOGGER.info("重复购票正确失败：{}", response2.getMessage());
            }
            
        } catch (Exception e) {
            LOGGER.error("测试异常", e);
        }
    }
    
    /**
     * 测试库存不足情况
     */
    @Test
    public void testInsufficientStock() {
        try {
            // 先查询当前票券信息
            LOGGER.info("开始测试库存不足情况");
            
            // 尝试购买一个不存在的日期或库存为0的日期
            PurchaseRequest request = new PurchaseRequest();
            request.setUserId(4001L);
            request.setDate("2025-01-22"); // 假设这个日期没有票券或库存为0
            
            ApiResponse<PurchaseRecord> response = ticketService.purchaseTicketWithPessimisticLockV2(request);
            
            if (response.getCode() == 200) {
                LOGGER.info("购票成功！票券编码: {}", response.getData().getTicketCode());
            } else {
                LOGGER.info("购票失败（预期结果）：{}", response.getMessage());
            }
            
        } catch (Exception e) {
            LOGGER.error("测试异常", e);
        }
    }
    
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(PessimisticLockV2Test.class);
}
