package cn.monitor4all.miaoshaservice.service;

import cn.monitor4all.miaoshadao.model.PurchaseRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * TicketCacheManager测试类
 * 测试缓存优先，数据库兜底的购买记录获取逻辑
 */
public class TicketCacheManagerTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private cn.monitor4all.miaoshadao.mapper.TicketPurchaseRecordMapper ticketPurchaseRecordMapper;

    @InjectMocks
    private cn.monitor4all.miaoshaservice.service.impl.TicketCacheManagerImpl ticketCacheManager;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void testGetPurchaseRecordsWithFallback_CacheHit() {
        // 准备测试数据
        Long userId = 12345L;
        List<PurchaseRecord> cachedRecords = createMockPurchaseRecords();
        String cachedJson = "[{\"userId\":12345,\"date\":\"2024-01-15\",\"ticketCode\":\"T20240115ABC123\"}]";

        // 模拟缓存命中
        when(valueOperations.get("purchase:" + userId)).thenReturn(cachedJson);

        // 执行测试
        List<PurchaseRecord> result = ticketCacheManager.getPurchaseRecordsWithFallback(userId);

        // 验证结果
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(userId, result.get(0).getUserId());
        assertEquals("T20240115ABC123", result.get(0).getTicketCode());

        // 验证没有调用数据库
        verify(ticketPurchaseRecordMapper, never()).selectByUserId(any());
    }

    @Test
    void testGetPurchaseRecordsWithFallback_CacheMiss_DatabaseHit() {
        // 准备测试数据
        Long userId = 12345L;
        List<cn.monitor4all.miaoshadao.dao.TicketPurchaseRecord> dbRecords = createMockDbRecords();

        // 模拟缓存未命中
        when(valueOperations.get("purchase:" + userId)).thenReturn(null);
        
        // 模拟数据库命中
        when(ticketPurchaseRecordMapper.selectByUserId(userId)).thenReturn(dbRecords);

        // 执行测试
        List<PurchaseRecord> result = ticketCacheManager.getPurchaseRecordsWithFallback(userId);

        // 验证结果
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(userId, result.get(0).getUserId());

        // 验证调用了数据库
        verify(ticketPurchaseRecordMapper).selectByUserId(userId);
        
        // 验证数据被同步到缓存
        verify(valueOperations).set(eq("purchase:" + userId), anyString(), eq(3600L), any());
    }

    @Test
    void testGetPurchaseRecordsWithFallback_CacheMiss_DatabaseMiss() {
        // 准备测试数据
        Long userId = 12345L;

        // 模拟缓存未命中
        when(valueOperations.get("purchase:" + userId)).thenReturn(null);
        
        // 模拟数据库未命中
        when(ticketPurchaseRecordMapper.selectByUserId(userId)).thenReturn(new ArrayList<>());

        // 执行测试
        List<PurchaseRecord> result = ticketCacheManager.getPurchaseRecordsWithFallback(userId);

        // 验证结果
        assertNotNull(result);
        assertTrue(result.isEmpty());

        // 验证调用了数据库
        verify(ticketPurchaseRecordMapper).selectByUserId(userId);
        
        // 验证没有同步到缓存（因为数据库也没有数据）
        verify(valueOperations, never()).set(eq("purchase:" + userId), anyString(), anyLong(), any());
    }

    @Test
    void testGetPurchaseRecordsWithFallback_Exception() {
        // 准备测试数据
        Long userId = 12345L;

        // 模拟异常
        when(valueOperations.get("purchase:" + userId)).thenThrow(new RuntimeException("Redis连接失败"));

        // 执行测试
        List<PurchaseRecord> result = ticketCacheManager.getPurchaseRecordsWithFallback(userId);

        // 验证结果
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    private List<PurchaseRecord> createMockPurchaseRecords() {
        List<PurchaseRecord> records = new ArrayList<>();
        PurchaseRecord record = new PurchaseRecord(12345L, LocalDate.of(2024, 1, 15), "T20240115ABC123");
        records.add(record);
        return records;
    }

    private List<cn.monitor4all.miaoshadao.dao.TicketPurchaseRecord> createMockDbRecords() {
        List<cn.monitor4all.miaoshadao.dao.TicketPurchaseRecord> records = new ArrayList<>();
        cn.monitor4all.miaoshadao.dao.TicketPurchaseRecord record = new cn.monitor4all.miaoshadao.dao.TicketPurchaseRecord();
        record.setUserId(12345L);
        record.setTicketDate("2024-01-15");
        record.setTicketCode("T20240115ABC123");
        records.add(record);
        return records;
    }
}
