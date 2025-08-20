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
 * 延迟双删缓存功能测试类
 */
@ExtendWith(MockitoExtension.class)
public class DelayedCacheDeleteTest {

    @Mock
    private TicketCacheManager ticketCacheManager;

    @Mock
    private CacheConfig cacheConfig;

    @InjectMocks
    private TicketServiceImpl ticketService;

    private String testDate;

    @BeforeEach
    void setUp() {
        testDate = "2025-01-15";
    }

    @Test
    void testUpdateCacheAfterPurchase_WithDelayedDeleteEnabled() throws Exception {
        // 准备测试数据 - 启用延迟双删
        when(cacheConfig.isDelayedDeleteEnabled()).thenReturn(true);
        when(cacheConfig.getDelayedDeleteDelay()).thenReturn(100L); // 100ms延迟

        // 执行测试
        updateCacheAfterPurchaseReflection(testDate);

        // 验证第一次删除缓存被调用
        verify(ticketCacheManager, times(1)).deleteTicket(testDate);

        // 等待延迟删除任务完成
        Thread.sleep(200);

        // 验证延迟删除缓存被调用
        verify(ticketCacheManager, times(2)).deleteTicket(testDate);
    }

    @Test
    void testUpdateCacheAfterPurchase_WithDelayedDeleteDisabled() throws Exception {
        // 准备测试数据 - 禁用延迟双删
        when(cacheConfig.isDelayedDeleteEnabled()).thenReturn(false);

        // 执行测试
        updateCacheAfterPurchaseReflection(testDate);

        // 验证第一次删除缓存被调用
        verify(ticketCacheManager, times(1)).deleteTicket(testDate);

        // 等待一段时间，确保没有延迟删除
        Thread.sleep(200);

        // 验证只调用了一次删除缓存
        verify(ticketCacheManager, times(1)).deleteTicket(testDate);
    }

    @Test
    void testUpdateCacheAfterPurchase_WithCustomDelay() throws Exception {
        // 准备测试数据 - 自定义延迟时间
        when(cacheConfig.isDelayedDeleteEnabled()).thenReturn(true);
        when(cacheConfig.getDelayedDeleteDelay()).thenReturn(50L); // 50ms延迟

        // 执行测试
        updateCacheAfterPurchaseReflection(testDate);

        // 验证第一次删除缓存被调用
        verify(ticketCacheManager, times(1)).deleteTicket(testDate);

        // 等待延迟删除任务完成
        Thread.sleep(100);

        // 验证延迟删除缓存被调用
        verify(ticketCacheManager, times(2)).deleteTicket(testDate);
    }

    @Test
    void testUpdateCacheAfterPurchase_WithCacheManagerException() throws Exception {
        // 准备测试数据 - 缓存管理器抛出异常
        when(cacheConfig.isDelayedDeleteEnabled()).thenReturn(true);
        when(cacheConfig.getDelayedDeleteDelay()).thenReturn(100L);
        doThrow(new RuntimeException("缓存删除失败")).when(ticketCacheManager).deleteTicket(testDate);

        // 执行测试
        updateCacheAfterPurchaseReflection(testDate);

        // 验证第一次删除缓存被调用（即使失败）
        verify(ticketCacheManager, times(1)).deleteTicket(testDate);

        // 等待延迟删除任务完成
        Thread.sleep(200);

        // 验证延迟删除缓存也被调用（即使第一次失败）
        verify(ticketCacheManager, times(2)).deleteTicket(testDate);
    }

    @Test
    void testUpdateCacheAfterPurchase_WithConfigException() throws Exception {
        // 准备测试数据 - 配置获取失败
        when(cacheConfig.isDelayedDeleteEnabled()).thenThrow(new RuntimeException("配置获取失败"));

        // 执行测试
        updateCacheAfterPurchaseReflection(testDate);

        // 验证第一次删除缓存被调用
        verify(ticketCacheManager, times(1)).deleteTicket(testDate);

        // 等待一段时间，确保没有延迟删除
        Thread.sleep(200);

        // 验证只调用了一次删除缓存
        verify(ticketCacheManager, times(1)).deleteTicket(testDate);
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
