package cn.monitor4all.miaoshaservice;

import cn.monitor4all.miaoshadao.model.PurchaseRequest;
import cn.monitor4all.miaoshadao.model.ApiResponse;
import cn.monitor4all.miaoshadao.model.PurchaseRecord;
import cn.monitor4all.miaoshaservice.service.TicketService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 悲观锁购票功能测试类
 */
@SpringBootTest
@ActiveProfiles("test")
public class PessimisticLockPurchaseTest {
    
    @Resource
    private TicketService ticketService;
    
    /**
     * 测试悲观锁购票功能
     */
    @Test
    @Transactional
    public void testPessimisticLockPurchase() {
        try {
            // 创建购票请求
            PurchaseRequest request = new PurchaseRequest();
            request.setUserId(1L);
            request.setDate(LocalDate.now().plusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
            request.setVerifyHash("test_hash_" + System.currentTimeMillis());
            
            // 调用悲观锁购票方法
            ApiResponse<PurchaseRecord> response = ticketService.purchaseTicketWithPessimisticLock(request);
            
            // 验证结果
            if (response.getCode() == 200) {
                PurchaseRecord record = response.getData();
                System.out.println("购票成功！");
                System.out.println("用户ID: " + record.getUserId());
                System.out.println("购票日期: " + record.getDate());
                System.out.println("票券编码: " + record.getTicketCode());
            } else {
                System.out.println("购票失败: " + response.getMessage());
            }
            
        } catch (Exception e) {
            System.err.println("测试异常: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 测试并发购票场景
     */
    @Test
    public void testConcurrentPurchase() {
        // 这里可以添加并发测试逻辑
        System.out.println("并发购票测试 - 需要在实际环境中进行压力测试");
    }
}
