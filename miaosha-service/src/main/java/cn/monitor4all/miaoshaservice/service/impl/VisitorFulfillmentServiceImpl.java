package cn.monitor4all.miaoshaservice.service.impl;

import cn.monitor4all.miaoshadao.entity.FulfillmentOutboxEvent;
import cn.monitor4all.miaoshadao.entity.FulfillmentTicketOrder;
import cn.monitor4all.miaoshadao.entity.VisitorReservationTask;
import cn.monitor4all.miaoshadao.mapper.FulfillmentMapper;
import cn.monitor4all.miaoshaservice.model.FulfillmentStatusView;
import cn.monitor4all.miaoshaservice.model.VisitorReservationEvent;
import cn.monitor4all.miaoshaservice.service.VisitorFulfillmentService;
import com.alibaba.fastjson.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * 购票权益与访客预约解耦实现。
 *
 * @author Qoder
 * @date 2026/09/20
 */
@Service
public class VisitorFulfillmentServiceImpl implements VisitorFulfillmentService {

    private static final Logger LOGGER = LoggerFactory.getLogger(VisitorFulfillmentServiceImpl.class);
    private static final Integer STOCK_ID = 22;
    private static final int MAX_AUTO_RETRY = 3;
    private static final long RETRY_DELAY_MS = 3_000L;

    @Resource
    private FulfillmentMapper fulfillmentMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FulfillmentStatusView purchaseTicket(Long userId, String visitSlot) {
        validatePurchase(userId, visitSlot);
        FulfillmentTicketOrder existingOrder = fulfillmentMapper.selectOrderByUserSlot(userId, visitSlot);
        if (existingOrder != null) {
            return toView(existingOrder, fulfillmentMapper.selectTask(existingOrder.getOrderNo()));
        }

        Date now = new Date();
        String orderNo = newIdentifier("TO");
        String reservationNo = newIdentifier("VR");
        String eventId = newIdentifier("EV");
        FulfillmentTicketOrder order = new FulfillmentTicketOrder();
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setStockId(STOCK_ID);
        order.setVisitSlot(visitSlot);
        order.setTicketStatus("TICKET_SUCCESS");
        order.setFulfillmentStatus("VISITOR_PENDING");
        order.setCreateTime(now);
        order.setUpdateTime(now);

        try {
            fulfillmentMapper.insertOrder(order);
        } catch (DataIntegrityViolationException exception) {
            throw new IllegalStateException("请勿重复提交，正在为你查询已购票状态");
        }
        if (fulfillmentMapper.reserveStock(STOCK_ID) != 1) {
            throw new IllegalStateException("当前场次名额已约满");
        }

        VisitorReservationTask task = new VisitorReservationTask();
        task.setOrderNo(orderNo);
        task.setReservationNo(reservationNo);
        task.setUserId(userId);
        task.setVisitSlot(visitSlot);
        task.setStatus("PENDING");
        task.setRetryCount(0);
        task.setNextRetryTime(now);
        task.setCreateTime(now);
        task.setUpdateTime(now);
        fulfillmentMapper.insertTask(task);

        FulfillmentOutboxEvent outboxEvent = new FulfillmentOutboxEvent();
        outboxEvent.setEventId(eventId);
        outboxEvent.setOrderNo(orderNo);
        outboxEvent.setEventType("VISITOR_RESERVATION_REQUESTED");
        outboxEvent.setPayload(JSON.toJSONString(new VisitorReservationEvent(eventId, orderNo)));
        outboxEvent.setStatus("PENDING");
        outboxEvent.setRetryCount(0);
        outboxEvent.setNextRetryTime(now);
        outboxEvent.setCreateTime(now);
        outboxEvent.setUpdateTime(now);
        fulfillmentMapper.insertOutbox(outboxEvent);
        LOGGER.info("购票权益已生效，等待访客预约履约，orderNo={}, userId={}", orderNo, userId);
        return toView(order, task);
    }

    @Override
    public FulfillmentStatusView getStatus(Long userId, String visitSlot) {
        validatePurchase(userId, visitSlot);
        FulfillmentTicketOrder order = fulfillmentMapper.selectOrderByUserSlot(userId, visitSlot);
        if (order == null) {
            return null;
        }
        return toView(order, fulfillmentMapper.selectTask(order.getOrderNo()));
    }

    @Override
    public Integer getRemainingQuota() {
        Integer remaining = fulfillmentMapper.selectStockRemaining(STOCK_ID);
        return remaining == null ? 0 : remaining;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void processVisitorReservation(VisitorReservationEvent event) {
        if (event == null || event.getEventId() == null || event.getOrderNo() == null) {
            throw new IllegalArgumentException("访客预约事件不完整");
        }
        Date now = new Date();
        VisitorReservationTask task = fulfillmentMapper.selectTask(event.getOrderNo());
        if (task == null || fulfillmentMapper.claimTask(event.getOrderNo(), now) != 1) {
            fulfillmentMapper.markEventConsumed(event.getEventId(), now);
            return;
        }

        GatewayResult result = requestVisitorSystem(task);
        if (result.success) {
            fulfillmentMapper.markTaskSuccess(task.getOrderNo(), "系统自动办理", now);
            fulfillmentMapper.markOrderFulfillment(task.getOrderNo(), "VISITOR_SUCCESS", now);
            fulfillmentMapper.markEventConsumed(event.getEventId(), now);
            LOGGER.info("访客预约成功，orderNo={}, reservationNo={}", task.getOrderNo(), task.getReservationNo());
            return;
        }

        int retryCount = task.getRetryCount() + 1;
        if (retryCount >= MAX_AUTO_RETRY) {
            fulfillmentMapper.markTaskManual(task.getOrderNo(), retryCount, result.code, result.message, now);
            fulfillmentMapper.markOrderFulfillment(task.getOrderNo(), "MANUAL_PENDING", now);
            fulfillmentMapper.markEventManual(event.getEventId(), now);
            LOGGER.warn("访客预约转人工兜底，orderNo={}, errorCode={}", task.getOrderNo(), result.code);
            return;
        }

        Date nextRetryTime = new Date(now.getTime() + RETRY_DELAY_MS * retryCount);
        fulfillmentMapper.markTaskRetry(task.getOrderNo(), retryCount, nextRetryTime, result.code, result.message, now);
        fulfillmentMapper.requeueEvent(event.getEventId(), nextRetryTime, now);
        LOGGER.warn("访客预约待自动补偿，orderNo={}, retryCount={}", task.getOrderNo(), retryCount);
    }

    @Override
    public List<VisitorReservationTask> listTasks(String status) {
        return fulfillmentMapper.selectTasks(status == null ? "" : status);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void retryManually(String orderNo) {
        VisitorReservationTask task = requireTask(orderNo);
        if (!"MANUAL_PENDING".equals(task.getStatus())) {
            throw new IllegalStateException("该任务未进入人工兜底，无需人工重试");
        }
        Date now = new Date();
        fulfillmentMapper.markTaskRetry(orderNo, task.getRetryCount(), now, "MANUAL_RETRY", "人工发起再次预约", now);
        fulfillmentMapper.markOrderFulfillment(orderNo, "VISITOR_PENDING", now);
        fulfillmentMapper.requeueEventByOrder(orderNo, now, now);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void completeManually(String orderNo, String operator) {
        VisitorReservationTask task = requireTask(orderNo);
        if (!"MANUAL_PENDING".equals(task.getStatus())) {
            throw new IllegalStateException("仅人工兜底任务可人工完成");
        }
        if (operator == null || operator.trim().isEmpty()) {
            throw new IllegalArgumentException("请填写处理人");
        }
        Date now = new Date();
        fulfillmentMapper.markTaskSuccess(orderNo, operator.trim(), now);
        fulfillmentMapper.markOrderFulfillment(orderNo, "VISITOR_SUCCESS", now);
        fulfillmentMapper.markEventManualByOrder(orderNo, now);
        LOGGER.info("人工完成访客预约，orderNo={}, operator={}", orderNo, operator);
    }

    private VisitorReservationTask requireTask(String orderNo) {
        if (orderNo == null || orderNo.trim().isEmpty()) {
            throw new IllegalArgumentException("订单号不能为空");
        }
        VisitorReservationTask task = fulfillmentMapper.selectTask(orderNo);
        if (task == null) {
            throw new IllegalArgumentException("未找到访客预约任务");
        }
        return task;
    }

    private void validatePurchase(Long userId, String visitSlot) {
        if (userId == null || userId <= 0L) {
            throw new IllegalArgumentException("预约人编号不正确");
        }
        if (!"SAT_AM".equals(visitSlot) && !"SAT_PM".equals(visitSlot) && !"SUN_AM".equals(visitSlot)) {
            throw new IllegalArgumentException("请选择可预约的参观场次");
        }
    }

    private FulfillmentStatusView toView(FulfillmentTicketOrder order, VisitorReservationTask task) {
        FulfillmentStatusView view = new FulfillmentStatusView();
        view.setOrderNo(order.getOrderNo());
        view.setUserId(order.getUserId());
        view.setVisitSlot(order.getVisitSlot());
        view.setTicketStatus(order.getTicketStatus());
        view.setFulfillmentStatus(order.getFulfillmentStatus());
        if (task != null) {
            view.setRetryCount(task.getRetryCount());
            view.setLastErrorMessage(task.getLastErrorMessage());
            if (task.getNextRetryTime() != null) {
                view.setNextRetryTime(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(task.getNextRetryTime()));
            }
        }
        return view;
    }

    private String newIdentifier(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "");
    }

    private GatewayResult requestVisitorSystem(VisitorReservationTask task) {
        long lastDigit = task.getUserId() % 10L;
        if (lastDigit == 8L) {
            return GatewayResult.failure("VISITOR_PROFILE_REVIEW", "访客资料需要人工核验");
        }
        if (lastDigit == 9L && task.getRetryCount() < 2) {
            return GatewayResult.failure("VISITOR_SYSTEM_BUSY", "访客系统繁忙，正在自动继续办理");
        }
        return GatewayResult.success();
    }

    private static class GatewayResult {
        private final boolean success;
        private final String code;
        private final String message;

        private GatewayResult(boolean success, String code, String message) {
            this.success = success;
            this.code = code;
            this.message = message;
        }

        private static GatewayResult success() {
            return new GatewayResult(true, null, null);
        }

        private static GatewayResult failure(String code, String message) {
            return new GatewayResult(false, code, message);
        }
    }
}
