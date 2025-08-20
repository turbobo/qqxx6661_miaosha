package cn.monitor4all.miaoshaservice.service;

import cn.monitor4all.miaoshaservice.service.impl.TicketServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 票券编码生成方法测试类
 */
@ExtendWith(MockitoExtension.class)
public class TicketCodeGenerationTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private TicketServiceImpl ticketService;

    private String testDate;
    private Long testUserId;

    @BeforeEach
    void setUp() {
        testDate = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        testUserId = 12345L;
        
        // 设置Redis操作Mock
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void testGenerateTicketCode_WithRedisSequence() {
        // 准备测试数据
        when(valueOperations.increment(anyString())).thenReturn(1L, 2L, 3L);
        when(stringRedisTemplate.expire(anyString(), anyLong(), any())).thenReturn(true);

        // 执行测试 - 多次生成票券编码
        String code1 = generateTicketCodeReflection(testUserId, testDate);
        String code2 = generateTicketCodeReflection(testUserId, testDate);
        String code3 = generateTicketCodeReflection(testUserId, testDate);

        // 验证结果
        assertNotNull(code1);
        assertNotNull(code2);
        assertNotNull(code3);
        
        // 验证格式：T + 日期 + 用户ID + 6位序列号
        assertTrue(code1.startsWith("T"));
        assertTrue(code1.contains(testUserId.toString()));
        
        // 验证唯一性
        Set<String> codes = new HashSet<>();
        codes.add(code1);
        codes.add(code2);
        codes.add(code3);
        assertEquals(3, codes.size(), "生成的票券编码应该唯一");

        // 验证Redis调用
        verify(valueOperations, times(3)).increment(anyString());
        verify(stringRedisTemplate, times(3)).expire(anyString(), anyLong(), any());
    }

    @Test
    void testGenerateTicketCode_RedisFailure_Fallback() {
        // 模拟Redis失败
        when(valueOperations.increment(anyString())).thenThrow(new RuntimeException("Redis连接失败"));

        // 执行测试
        String code = generateTicketCodeReflection(testUserId, testDate);

        // 验证结果
        assertNotNull(code);
        assertTrue(code.startsWith("T"));
        assertTrue(code.contains(testUserId.toString()));
        
        // 验证使用了降级方案
        verify(valueOperations, times(1)).increment(anyString());
    }

    @Test
    void testGenerateTicketCode_ConcurrentGeneration() throws InterruptedException {
        // 准备测试数据
        when(valueOperations.increment(anyString())).thenReturn(1L, 2L, 3L, 4L, 5L);
        when(stringRedisTemplate.expire(anyString(), anyLong(), any())).thenReturn(true);

        // 并发测试
        Set<String> codes = new HashSet<>();
        Thread[] threads = new Thread[5];

        for (int i = 0; i < 5; i++) {
            threads[i] = new Thread(() -> {
                String code = generateTicketCodeReflection(testUserId, testDate);
                synchronized (codes) {
                    codes.add(code);
                }
            });
        }

        // 启动所有线程
        for (Thread thread : threads) {
            thread.start();
        }

        // 等待所有线程完成
        for (Thread thread : threads) {
            thread.join();
        }

        // 验证结果
        assertEquals(5, codes.size(), "并发生成的票券编码应该唯一");
        
        // 验证Redis调用次数
        verify(valueOperations, times(5)).increment(anyString());
    }

    @Test
    void testGenerateTicketCode_DifferentDates() {
        // 准备测试数据
        when(valueOperations.increment(anyString())).thenReturn(1L);
        when(stringRedisTemplate.expire(anyString(), anyLong(), any())).thenReturn(true);

        String date1 = "2025-01-15";
        String date2 = "2025-01-16";

        // 执行测试
        String code1 = generateTicketCodeReflection(testUserId, date1);
        String code2 = generateTicketCodeReflection(testUserId, date2);

        // 验证结果
        assertNotNull(code1);
        assertNotNull(code2);
        assertNotEquals(code1, code2, "不同日期应该生成不同的票券编码");

        // 验证Redis key不同
        verify(valueOperations).increment(contains("20250115"));
        verify(valueOperations).increment(contains("20250116"));
    }

    @Test
    void testGenerateTicketCode_DifferentUsers() {
        // 准备测试数据
        when(valueOperations.increment(anyString())).thenReturn(1L);
        when(stringRedisTemplate.expire(anyString(), anyLong(), any())).thenReturn(true);

        Long userId1 = 12345L;
        Long userId2 = 67890L;

        // 执行测试
        String code1 = generateTicketCodeReflection(userId1, testDate);
        String code2 = generateTicketCodeReflection(userId2, testDate);

        // 验证结果
        assertNotNull(code1);
        assertNotNull(code2);
        assertNotEquals(code1, code2, "不同用户应该生成不同的票券编码");
        
        assertTrue(code1.contains(userId1.toString()));
        assertTrue(code2.contains(userId2.toString()));
    }

    @Test
    void testGenerateTicketCode_FormatValidation() {
        // 准备测试数据
        when(valueOperations.increment(anyString())).thenReturn(1L);
        when(stringRedisTemplate.expire(anyString(), anyLong(), any())).thenReturn(true);

        // 执行测试
        String code = generateTicketCodeReflection(testUserId, testDate);

        // 验证格式
        assertNotNull(code);
        assertTrue(code.startsWith("T"), "票券编码应该以T开头");
        
        // 验证长度（T + 8位日期 + 用户ID + 6位序列号）
        int expectedLength = 1 + 8 + testUserId.toString().length() + 6;
        assertEquals(expectedLength, code.length(), "票券编码长度应该正确");
        
        // 验证包含必要信息
        String dateStr = testDate.replace("-", "").replace(".", "");
        assertTrue(code.contains(dateStr), "票券编码应该包含日期信息");
        assertTrue(code.contains(testUserId.toString()), "票券编码应该包含用户ID");
    }

    /**
     * 通过反射调用私有方法进行测试
     */
    private String generateTicketCodeReflection(Long userId, String date) {
        try {
            java.lang.reflect.Method method = TicketServiceImpl.class.getDeclaredMethod("generateTicketCode", Long.class, String.class);
            method.setAccessible(true);
            return (String) method.invoke(ticketService, userId, date);
        } catch (Exception e) {
            throw new RuntimeException("反射调用失败", e);
        }
    }
}
