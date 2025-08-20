package cn.monitor4all.miaoshaservice.service;

import cn.monitor4all.miaoshadao.dao.TicketOrder;
import cn.monitor4all.miaoshaservice.service.impl.TicketOrderServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import cn.monitor4all.miaoshadao.mapper.TicketOrderMapper;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 票券订单服务测试类
 */
@ExtendWith(MockitoExtension.class)
public class TicketOrderServiceTest {

    @Mock
    private TicketOrderMapper ticketOrderMapper;

    @InjectMocks
    private TicketOrderServiceImpl ticketOrderService;

    private TicketOrder testTicketOrder;
    private Long testUserId;
    private Integer testTicketId;
    private String testTicketCode;
    private String testTicketDate;
    private Long testAmount;

    @BeforeEach
    void setUp() {
        testUserId = 12345L;
        testTicketId = 1;
        testTicketCode = "T2025011512345000001";
        testTicketDate = "2025-01-15";
        testAmount = 10000L;

        testTicketOrder = new TicketOrder();
        testTicketOrder.setId(1);
        testTicketOrder.setOrderNo("TO1705123456789001");
        testTicketOrder.setUserId(testUserId);
        testTicketOrder.setTicketId(testTicketId);
        testTicketOrder.setTicketCode(testTicketCode);
        testTicketOrder.setTicketDate(testTicketDate);
        testTicketOrder.setStatus(1);
        testTicketOrder.setAmount(testAmount);
    }

    @Test
    void testCreateTicketOrder_Success() {
        // 准备测试数据
        when(ticketOrderMapper.insert(any(TicketOrder.class))).thenReturn(1);

        // 执行测试
        TicketOrder result = ticketOrderService.createTicketOrder(
            testUserId, testTicketId, testTicketCode, testTicketDate, testAmount
        );

        // 验证结果
        assertNotNull(result);
        assertEquals(testUserId, result.getUserId());
        assertEquals(testTicketId, result.getTicketId());
        assertEquals(testTicketCode, result.getTicketCode());
        assertEquals(testTicketDate, result.getTicketDate());
        assertEquals(testAmount, result.getAmount());
        assertEquals(1, result.getStatus()); // 默认待支付状态

        // 验证调用
        verify(ticketOrderMapper, times(1)).insert(any(TicketOrder.class));
    }

    @Test
    void testCreateTicketOrder_InsertFailed() {
        // 准备测试数据
        when(ticketOrderMapper.insert(any(TicketOrder.class))).thenReturn(0);

        // 执行测试并验证异常
        assertThrows(RuntimeException.class, () -> {
            ticketOrderService.createTicketOrder(
                testUserId, testTicketId, testTicketCode, testTicketDate, testAmount
            );
        });

        // 验证调用
        verify(ticketOrderMapper, times(1)).insert(any(TicketOrder.class));
    }

    @Test
    void testGetTicketOrderById_Success() {
        // 准备测试数据
        when(ticketOrderMapper.selectByPrimaryKey(testTicketOrder.getId())).thenReturn(testTicketOrder);

        // 执行测试
        TicketOrder result = ticketOrderService.getTicketOrderById(testTicketOrder.getId());

        // 验证结果
        assertNotNull(result);
        assertEquals(testTicketOrder.getId(), result.getId());
        assertEquals(testTicketOrder.getOrderNo(), result.getOrderNo());

        // 验证调用
        verify(ticketOrderMapper, times(1)).selectByPrimaryKey(testTicketOrder.getId());
    }

    @Test
    void testGetTicketOrderById_NotFound() {
        // 准备测试数据
        when(ticketOrderMapper.selectByPrimaryKey(anyInt())).thenReturn(null);

        // 执行测试
        TicketOrder result = ticketOrderService.getTicketOrderById(999);

        // 验证结果
        assertNull(result);

        // 验证调用
        verify(ticketOrderMapper, times(1)).selectByPrimaryKey(999);
    }

    @Test
    void testGetTicketOrderByOrderNo_Success() {
        // 准备测试数据
        when(ticketOrderMapper.selectByOrderNo(testTicketOrder.getOrderNo())).thenReturn(testTicketOrder);

        // 执行测试
        TicketOrder result = ticketOrderService.getTicketOrderByOrderNo(testTicketOrder.getOrderNo());

        // 验证结果
        assertNotNull(result);
        assertEquals(testTicketOrder.getOrderNo(), result.getOrderNo());

        // 验证调用
        verify(ticketOrderMapper, times(1)).selectByOrderNo(testTicketOrder.getOrderNo());
    }

    @Test
    void testGetTicketOrdersByUserId_Success() {
        // 准备测试数据
        List<TicketOrder> expectedOrders = Arrays.asList(testTicketOrder);
        when(ticketOrderMapper.selectByUserId(testUserId)).thenReturn(expectedOrders);

        // 执行测试
        List<TicketOrder> result = ticketOrderService.getTicketOrdersByUserId(testUserId);

        // 验证结果
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(testUserId, result.get(0).getUserId());

        // 验证调用
        verify(ticketOrderMapper, times(1)).selectByUserId(testUserId);
    }

    @Test
    void testUpdateTicketOrderStatus_Success() {
        // 准备测试数据
        when(ticketOrderMapper.selectByOrderNo(testTicketOrder.getOrderNo())).thenReturn(testTicketOrder);
        when(ticketOrderMapper.updateByPrimaryKey(any(TicketOrder.class))).thenReturn(1);

        // 执行测试
        boolean result = ticketOrderService.updateTicketOrderStatus(testTicketOrder.getOrderNo(), 2);

        // 验证结果
        assertTrue(result);

        // 验证调用
        verify(ticketOrderMapper, times(1)).selectByOrderNo(testTicketOrder.getOrderNo());
        verify(ticketOrderMapper, times(1)).updateByPrimaryKey(any(TicketOrder.class));
    }

    @Test
    void testUpdateTicketOrderStatus_OrderNotFound() {
        // 准备测试数据
        when(ticketOrderMapper.selectByOrderNo(anyString())).thenReturn(null);

        // 执行测试
        boolean result = ticketOrderService.updateTicketOrderStatus("NOT_EXIST", 2);

        // 验证结果
        assertFalse(result);

        // 验证调用
        verify(ticketOrderMapper, times(1)).selectByOrderNo("NOT_EXIST");
        verify(ticketOrderMapper, never()).updateByPrimaryKey(any(TicketOrder.class));
    }

    @Test
    void testPayTicketOrder_Success() {
        // 准备测试数据
        when(ticketOrderMapper.selectByOrderNo(testTicketOrder.getOrderNo())).thenReturn(testTicketOrder);
        when(ticketOrderMapper.updateByPrimaryKey(any(TicketOrder.class))).thenReturn(1);

        // 执行测试
        boolean result = ticketOrderService.payTicketOrder(testTicketOrder.getOrderNo());

        // 验证结果
        assertTrue(result);

        // 验证调用
        verify(ticketOrderMapper, times(1)).selectByOrderNo(testTicketOrder.getOrderNo());
        verify(ticketOrderMapper, times(1)).updateByPrimaryKey(any(TicketOrder.class));
    }

    @Test
    void testPayTicketOrder_WrongStatus() {
        // 准备测试数据 - 设置订单状态为已支付
        testTicketOrder.setStatus(2);
        when(ticketOrderMapper.selectByOrderNo(testTicketOrder.getOrderNo())).thenReturn(testTicketOrder);

        // 执行测试
        boolean result = ticketOrderService.payTicketOrder(testTicketOrder.getOrderNo());

        // 验证结果
        assertFalse(result);

        // 验证调用
        verify(ticketOrderMapper, times(1)).selectByOrderNo(testTicketOrder.getOrderNo());
        verify(ticketOrderMapper, never()).updateByPrimaryKey(any(TicketOrder.class));
    }

    @Test
    void testCancelTicketOrder_Success() {
        // 准备测试数据
        when(ticketOrderMapper.selectByOrderNo(testTicketOrder.getOrderNo())).thenReturn(testTicketOrder);
        when(ticketOrderMapper.updateByPrimaryKey(any(TicketOrder.class))).thenReturn(1);

        // 执行测试
        boolean result = ticketOrderService.cancelTicketOrder(testTicketOrder.getOrderNo());

        // 验证结果
        assertTrue(result);

        // 验证调用
        verify(ticketOrderMapper, times(1)).selectByOrderNo(testTicketOrder.getOrderNo());
        verify(ticketOrderMapper, times(1)).updateByPrimaryKey(any(TicketOrder.class));
    }

    @Test
    void testDeleteTicketOrderById_Success() {
        // 准备测试数据
        when(ticketOrderMapper.deleteByPrimaryKey(testTicketOrder.getId())).thenReturn(1);

        // 执行测试
        boolean result = ticketOrderService.deleteTicketOrderById(testTicketOrder.getId());

        // 验证结果
        assertTrue(result);

        // 验证调用
        verify(ticketOrderMapper, times(1)).deleteByPrimaryKey(testTicketOrder.getId());
    }

    @Test
    void testDeleteTicketOrderById_NotFound() {
        // 准备测试数据
        when(ticketOrderMapper.deleteByPrimaryKey(anyInt())).thenReturn(0);

        // 执行测试
        boolean result = ticketOrderService.deleteTicketOrderById(999);

        // 验证结果
        assertFalse(result);

        // 验证调用
        verify(ticketOrderMapper, times(1)).deleteByPrimaryKey(999);
    }

    @Test
    void testDeleteTicketOrderByOrderNo_Success() {
        // 准备测试数据
        when(ticketOrderMapper.deleteByOrderNo(testTicketOrder.getOrderNo())).thenReturn(1);

        // 执行测试
        boolean result = ticketOrderService.deleteTicketOrderByOrderNo(testTicketOrder.getOrderNo());

        // 验证结果
        assertTrue(result);

        // 验证调用
        verify(ticketOrderMapper, times(1)).deleteByOrderNo(testTicketOrder.getOrderNo());
    }
}
