package cn.monitor4all.miaoshaservice.service;

import cn.monitor4all.miaoshadao.entity.FulfillmentTicketOrder;
import cn.monitor4all.miaoshadao.entity.VisitorReservationTask;
import cn.monitor4all.miaoshadao.mapper.FulfillmentMapper;
import cn.monitor4all.miaoshaservice.model.FulfillmentStatusView;
import cn.monitor4all.miaoshaservice.model.VisitorReservationEvent;
import cn.monitor4all.miaoshaservice.service.impl.VisitorFulfillmentServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 购票权益与访客预约履约服务测试类。
 *
 * @author Qoder
 * @date 2026/09/20
 */
@ExtendWith(MockitoExtension.class)
public class VisitorFulfillmentServiceImplTest {

    @Mock
    private FulfillmentMapper fulfillmentMapper;

    @InjectMocks
    private VisitorFulfillmentServiceImpl visitorFulfillmentService;

    private Long userId;
    private String visitSlot;

    @BeforeEach
    void setUp() {
        userId = 1001L;
        visitSlot = "SAT_AM";
    }

    @Test
    void testPurchaseTicketIdempotentWhenOrderExists() {
        FulfillmentTicketOrder order = new FulfillmentTicketOrder();
        order.setOrderNo("TO0001");
        order.setUserId(userId);
        order.setVisitSlot(visitSlot);
        order.setTicketStatus("TICKET_SUCCESS");
        order.setFulfillmentStatus("VISITOR_PENDING");
        when(fulfillmentMapper.selectOrderByUserSlot(userId, visitSlot)).thenReturn(order);

        FulfillmentStatusView view = visitorFulfillmentService.purchaseTicket(userId, visitSlot);

        assertNotNull(view);
        assertEquals("TO0001", view.getOrderNo());
        verify(fulfillmentMapper, never()).insertOrder(any(FulfillmentTicketOrder.class));
    }

    @Test
    void testPurchaseTicketRejectsInvalidVisitSlot() {
        assertThrows(IllegalArgumentException.class,
                () -> visitorFulfillmentService.purchaseTicket(userId, "MON_AM"));
    }

    @Test
    void testProcessVisitorReservationSuccessPath() {
        VisitorReservationTask task = new VisitorReservationTask();
        task.setOrderNo("TO0002");
        task.setReservationNo("VR0002");
        task.setUserId(userId);
        task.setStatus("PENDING");
        task.setRetryCount(0);
        when(fulfillmentMapper.selectTask("TO0002")).thenReturn(task);
        when(fulfillmentMapper.claimTask(eq("TO0002"), any(Date.class))).thenReturn(1);

        visitorFulfillmentService.processVisitorReservation(new VisitorReservationEvent("EV0002", "TO0002"));

        verify(fulfillmentMapper).markTaskSuccess(eq("TO0002"), anyString(), any(Date.class));
        verify(fulfillmentMapper).markOrderFulfillment(eq("TO0002"), eq("VISITOR_SUCCESS"), any(Date.class));
        verify(fulfillmentMapper).markEventConsumed(eq("EV0002"), any(Date.class));
    }

    @Test
    void testProcessVisitorReservationTurnsManualAfterMaxRetry() {
        VisitorReservationTask task = new VisitorReservationTask();
        task.setOrderNo("TO0008");
        task.setReservationNo("VR0008");
        task.setUserId(1008L);
        task.setStatus("RETRYING");
        task.setRetryCount(2);
        when(fulfillmentMapper.selectTask("TO0008")).thenReturn(task);
        when(fulfillmentMapper.claimTask(eq("TO0008"), any(Date.class))).thenReturn(1);

        visitorFulfillmentService.processVisitorReservation(new VisitorReservationEvent("EV0008", "TO0008"));

        verify(fulfillmentMapper).markTaskManual(eq("TO0008"), eq(3), anyString(), anyString(), any(Date.class));
        verify(fulfillmentMapper).markOrderFulfillment(eq("TO0008"), eq("MANUAL_PENDING"), any(Date.class));
    }

    @Test
    void testGetStatusReturnsNullWhenNoOrder() {
        when(fulfillmentMapper.selectOrderByUserSlot(userId, visitSlot)).thenReturn(null);

        assertNull(visitorFulfillmentService.getStatus(userId, visitSlot));
    }
}
