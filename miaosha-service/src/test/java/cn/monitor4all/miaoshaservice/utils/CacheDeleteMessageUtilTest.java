package cn.monitor4all.miaoshaservice.utils;

import cn.monitor4all.miaoshaservice.service.CacheDeleteMessageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 缓存删除消息工具类测试
 */
@ExtendWith(MockitoExtension.class)
public class CacheDeleteMessageUtilTest {

    @Mock
    private CacheDeleteMessageService cacheDeleteMessageService;

    @InjectMocks
    private CacheDeleteMessageUtil cacheDeleteMessageUtil;

    private String testCacheKey;
    private String testReason;
    private List<String> testCacheKeys;

    @BeforeEach
    void setUp() {
        testCacheKey = "test-cache-key";
        testReason = "test-reason";
        testCacheKeys = Arrays.asList("key1", "key2", "key3");
    }

    @Test
    void testSendCacheDeleteMessage_Success() {
        // 准备测试数据
        when(cacheDeleteMessageService.isConnected()).thenReturn(true);
        when(cacheDeleteMessageService.sendCacheDeleteMessage(anyString(), anyString())).thenReturn(true);

        // 执行测试
        boolean result = cacheDeleteMessageUtil.sendCacheDeleteMessage(testCacheKey, testReason);

        // 验证结果
        assert result;
        verify(cacheDeleteMessageService, times(1)).isConnected();
        verify(cacheDeleteMessageService, times(1)).sendCacheDeleteMessage(testCacheKey, testReason);
    }

    @Test
    void testSendCacheDeleteMessage_ServiceUnavailable() {
        // 准备测试数据 - 服务不可用
        when(cacheDeleteMessageService.isConnected()).thenReturn(false);

        // 执行测试
        boolean result = cacheDeleteMessageUtil.sendCacheDeleteMessage(testCacheKey, testReason);

        // 验证结果
        assert !result;
        verify(cacheDeleteMessageService, times(1)).isConnected();
        verify(cacheDeleteMessageService, never()).sendCacheDeleteMessage(anyString(), anyString());
    }

    @Test
    void testSendCacheDeleteMessage_ServiceFailure() {
        // 准备测试数据 - 服务可用但发送失败
        when(cacheDeleteMessageService.isConnected()).thenReturn(true);
        when(cacheDeleteMessageService.sendCacheDeleteMessage(anyString(), anyString())).thenReturn(false);

        // 执行测试
        boolean result = cacheDeleteMessageUtil.sendCacheDeleteMessage(testCacheKey, testReason);

        // 验证结果
        assert !result;
        verify(cacheDeleteMessageService, times(1)).isConnected();
        verify(cacheDeleteMessageService, times(1)).sendCacheDeleteMessage(testCacheKey, testReason);
    }

    @Test
    void testSendDelayedCacheDeleteMessage_Success() {
        // 准备测试数据
        long delaySeconds = 5L;
        when(cacheDeleteMessageService.isConnected()).thenReturn(true);
        when(cacheDeleteMessageService.sendDelayedCacheDeleteMessage(anyString(), anyString(), anyLong())).thenReturn(true);

        // 执行测试
        boolean result = cacheDeleteMessageUtil.sendDelayedCacheDeleteMessage(testCacheKey, testReason, delaySeconds);

        // 验证结果
        assert result;
        verify(cacheDeleteMessageService, times(1)).isConnected();
        verify(cacheDeleteMessageService, times(1)).sendDelayedCacheDeleteMessage(testCacheKey, testReason, delaySeconds);
    }

    @Test
    void testSendBatchCacheDeleteMessage_Success() {
        // 准备测试数据
        when(cacheDeleteMessageService.isConnected()).thenReturn(true);
        when(cacheDeleteMessageService.sendBatchCacheDeleteMessage(anyList(), anyString())).thenReturn(2);

        // 执行测试
        int result = cacheDeleteMessageUtil.sendBatchCacheDeleteMessage(testCacheKeys, testReason);

        // 验证结果
        assert result == 2;
        verify(cacheDeleteMessageService, times(1)).isConnected();
        verify(cacheDeleteMessageService, times(1)).sendBatchCacheDeleteMessage(testCacheKeys, testReason);
    }

    @Test
    void testSendBatchCacheDeleteMessage_EmptyList() {
        // 准备测试数据 - 空列表
        List<String> emptyKeys = Arrays.asList();
        when(cacheDeleteMessageService.isConnected()).thenReturn(true);
        when(cacheDeleteMessageService.sendBatchCacheDeleteMessage(anyList(), anyString())).thenReturn(0);

        // 执行测试
        int result = cacheDeleteMessageUtil.sendBatchCacheDeleteMessage(emptyKeys, testReason);

        // 验证结果
        assert result == 0;
        verify(cacheDeleteMessageService, times(1)).isConnected();
        verify(cacheDeleteMessageService, times(1)).sendBatchCacheDeleteMessage(emptyKeys, testReason);
    }

    @Test
    void testSendBatchCacheDeleteMessage_NullList() {
        // 准备测试数据 - null列表
        when(cacheDeleteMessageService.isConnected()).thenReturn(true);
        when(cacheDeleteMessageService.sendBatchCacheDeleteMessage(isNull(), anyString())).thenReturn(0);

        // 执行测试
        int result = cacheDeleteMessageUtil.sendBatchCacheDeleteMessage(null, testReason);

        // 验证结果
        assert result == 0;
        verify(cacheDeleteMessageService, times(1)).isConnected();
        verify(cacheDeleteMessageService, times(1)).sendBatchCacheDeleteMessage(null, testReason);
    }

    @Test
    void testSendTicketCacheDeleteMessage_Success() {
        // 准备测试数据
        String ticketDate = "2025-01-15";
        when(cacheDeleteMessageService.isConnected()).thenReturn(true);
        when(cacheDeleteMessageService.sendCacheDeleteMessage(anyString(), anyString())).thenReturn(true);

        // 执行测试
        boolean result = cacheDeleteMessageUtil.sendTicketCacheDeleteMessage(ticketDate, testReason);

        // 验证结果
        assert result;
        verify(cacheDeleteMessageService, times(1)).isConnected();
        verify(cacheDeleteMessageService, times(1)).sendCacheDeleteMessage(ticketDate, testReason);
    }

    @Test
    void testSendTicketDelayedCacheDeleteMessage_Success() {
        // 准备测试数据
        String ticketDate = "2025-01-15";
        long delaySeconds = 10L;
        when(cacheDeleteMessageService.isConnected()).thenReturn(true);
        when(cacheDeleteMessageService.sendDelayedCacheDeleteMessage(anyString(), anyString(), anyLong())).thenReturn(true);

        // 执行测试
        boolean result = cacheDeleteMessageUtil.sendTicketDelayedCacheDeleteMessage(ticketDate, testReason, delaySeconds);

        // 验证结果
        assert result;
        verify(cacheDeleteMessageService, times(1)).isConnected();
        verify(cacheDeleteMessageService, times(1)).sendDelayedCacheDeleteMessage(ticketDate, testReason, delaySeconds);
    }

    @Test
    void testSendTicketBatchCacheDeleteMessage_Success() {
        // 准备测试数据
        List<String> ticketDates = Arrays.asList("2025-01-15", "2025-01-16", "2025-01-17");
        when(cacheDeleteMessageService.isConnected()).thenReturn(true);
        when(cacheDeleteMessageService.sendBatchCacheDeleteMessage(anyList(), anyString())).thenReturn(3);

        // 执行测试
        int result = cacheDeleteMessageUtil.sendTicketBatchCacheDeleteMessage(ticketDates, testReason);

        // 验证结果
        assert result == 3;
        verify(cacheDeleteMessageService, times(1)).isConnected();
        verify(cacheDeleteMessageService, times(1)).sendBatchCacheDeleteMessage(ticketDates, testReason);
    }

    @Test
    void testIsMessageQueueAvailable_Connected() {
        // 准备测试数据
        when(cacheDeleteMessageService.isConnected()).thenReturn(true);

        // 执行测试
        boolean result = cacheDeleteMessageUtil.isMessageQueueAvailable();

        // 验证结果
        assert result;
        verify(cacheDeleteMessageService, times(1)).isConnected();
    }

    @Test
    void testIsMessageQueueAvailable_Disconnected() {
        // 准备测试数据
        when(cacheDeleteMessageService.isConnected()).thenReturn(false);

        // 执行测试
        boolean result = cacheDeleteMessageUtil.isMessageQueueAvailable();

        // 验证结果
        assert !result;
        verify(cacheDeleteMessageService, times(1)).isConnected();
    }

    @Test
    void testIsMessageQueueAvailable_Exception() {
        // 准备测试数据 - 抛出异常
        when(cacheDeleteMessageService.isConnected()).thenThrow(new RuntimeException("连接异常"));

        // 执行测试
        boolean result = cacheDeleteMessageUtil.isMessageQueueAvailable();

        // 验证结果
        assert !result;
        verify(cacheDeleteMessageService, times(1)).isConnected();
    }
}
