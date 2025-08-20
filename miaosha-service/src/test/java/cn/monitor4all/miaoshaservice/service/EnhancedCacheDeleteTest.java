package cn.monitor4all.miaoshaservice.service;

import cn.monitor4all.miaoshaservice.config.CacheConfig;
import cn.monitor4all.miaoshaservice.service.impl.TicketServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import cn.monitor4all.miaoshaservice.service.TicketCacheManager;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 增强的缓存删除功能测试类
 * 测试线程池异步执行和消息队列功能
 */
@ExtendWith(MockitoExtension.class)
public class EnhancedCacheDeleteTest {

    @Mock
    private TicketCacheManager ticketCacheManager;

    @Mock
    private CacheConfig cacheConfig;

    @Mock
    private CacheDeleteMessageService cacheDeleteMessageService;

    @InjectMocks
    private TicketServiceImpl ticketService;

    private String testDate;

    @BeforeEach
    void setUp() {
        testDate = "2025-01-15";
    }

    @Test
    void testUpdateCacheAfterPurchase_WithThreadPoolAndMessageQueue() throws Exception {
        // 准备测试数据 - 启用延迟双删和消息队列
        when(cacheConfig.isDelayedDeleteEnabled()).thenReturn(true);
        when(cacheConfig.getDelayedDeleteDelay()).thenReturn(100L); // 100ms延迟
        when(cacheDeleteMessageService.isConnected()).thenReturn(true);
        when(cacheDeleteMessageService.sendCacheDeleteMessage(anyString(), anyString())).thenReturn(true);

        // 执行测试
        updateCacheAfterPurchaseReflection(testDate);

        // 验证第一次删除缓存被调用
        verify(ticketCacheManager, times(1)).deleteTicket(testDate);

        // 等待延迟删除任务完成
        Thread.sleep(200);

        // 验证延迟删除缓存被调用
        verify(ticketCacheManager, times(2)).deleteTicket(testDate);

        // 验证消息队列被调用（延迟删除成功后的最终确认）
        verify(cacheDeleteMessageService, times(1)).sendCacheDeleteMessage(
            eq(testDate), eq("延迟删除成功后的最终确认"));
    }

    @Test
    void testUpdateCacheAfterPurchase_WithDelayedDeleteFailure() throws Exception {
        // 准备测试数据 - 延迟删除失败
        when(cacheConfig.isDelayedDeleteEnabled()).thenReturn(true);
        when(cacheConfig.getDelayedDeleteDelay()).thenReturn(100L);
        when(cacheDeleteMessageService.isConnected()).thenReturn(true);
        when(cacheDeleteMessageService.sendCacheDeleteMessage(anyString(), anyString())).thenReturn(true);
        
        // 模拟延迟删除失败
        doThrow(new RuntimeException("缓存删除失败")).when(ticketCacheManager).deleteTicket(testDate);

        // 执行测试
        updateCacheAfterPurchaseReflection(testDate);

        // 验证第一次删除缓存被调用
        verify(ticketCacheManager, times(1)).deleteTicket(testDate);

        // 等待延迟删除任务完成
        Thread.sleep(200);

        // 验证延迟删除缓存被调用（即使失败）
        verify(ticketCacheManager, times(2)).deleteTicket(testDate);

        // 验证补偿删除消息被发送
        verify(cacheDeleteMessageService, times(1)).sendCacheDeleteMessage(
            eq(testDate), eq("延迟删除失败后的补偿删除"));
    }

    @Test
    void testUpdateCacheAfterPurchase_WithMessageQueueUnavailable() throws Exception {
        // 准备测试数据 - 消息队列不可用
        when(cacheConfig.isDelayedDeleteEnabled()).thenReturn(true);
        when(cacheConfig.getDelayedDeleteDelay()).thenReturn(100L);
        when(cacheDeleteMessageService.isConnected()).thenReturn(false);

        // 执行测试
        updateCacheAfterPurchaseReflection(testDate);

        // 验证第一次删除缓存被调用
        verify(ticketCacheManager, times(1)).deleteTicket(testDate);

        // 等待延迟删除任务完成
        Thread.sleep(200);

        // 验证延迟删除缓存被调用
        verify(ticketCacheManager, times(2)).deleteTicket(testDate);

        // 验证消息队列没有被调用
        verify(cacheDeleteMessageService, never()).sendCacheDeleteMessage(anyString(), anyString());
    }

    @Test
    void testUpdateCacheAfterPurchase_WithSchedulingFailure() throws Exception {
        // 准备测试数据 - 调度失败
        when(cacheConfig.isDelayedDeleteEnabled()).thenReturn(true);
        when(cacheConfig.getDelayedDeleteDelay()).thenThrow(new RuntimeException("配置获取失败"));
        when(cacheDeleteMessageService.isConnected()).thenReturn(true);
        when(cacheDeleteMessageService.sendCacheDeleteMessage(anyString(), anyString())).thenReturn(true);

        // 执行测试
        updateCacheAfterPurchaseReflection(testDate);

        // 验证第一次删除缓存被调用
        verify(ticketCacheManager, times(1)).deleteTicket(testDate);

        // 验证调度失败后的消息队列删除被调用
        verify(cacheDeleteMessageService, times(1)).sendCacheDeleteMessage(
            eq(testDate), eq("调度失败后的消息队列删除"));
    }

    @Test
    void testUpdateCacheAfterPurchase_WithCustomDelay() throws Exception {
        // 准备测试数据 - 自定义延迟时间
        when(cacheConfig.isDelayedDeleteEnabled()).thenReturn(true);
        when(cacheConfig.getDelayedDeleteDelay()).thenReturn(50L); // 50ms延迟
        when(cacheDeleteMessageService.isConnected()).thenReturn(true);
        when(cacheDeleteMessageService.sendCacheDeleteMessage(anyString(), anyString())).thenReturn(true);

        // 执行测试
        updateCacheAfterPurchaseReflection(testDate);

        // 验证第一次删除缓存被调用
        verify(ticketCacheManager, times(1)).deleteTicket(testDate);

        // 等待延迟删除任务完成
        Thread.sleep(100);

        // 验证延迟删除缓存被调用
        verify(ticketCacheManager, times(2)).deleteTicket(testDate);

        // 验证消息队列被调用
        verify(cacheDeleteMessageService, times(1)).sendCacheDeleteMessage(
            eq(testDate), eq("延迟删除成功后的最终确认"));
    }

    /**
     * 通过反射调用私有方法进行测试
     */
    private void updateCacheAfterPurchaseReflection(String date) {
        try {
            java.lang.reflect.Method method = TicketServiceImpl.class.getDeclaredMethod("updateCacheAfterPurchase", String.class);
            method.setAccessible(true);
            method.invoke(ticketService, date);
        } catch (Exception e) {
            throw new RuntimeException("反射调用失败", e);
        }
    }
}
