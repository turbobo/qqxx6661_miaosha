package cn.monitor4all.miaoshadao.mapper;

import cn.monitor4all.miaoshadao.entity.FulfillmentOutboxEvent;
import cn.monitor4all.miaoshadao.entity.FulfillmentTicketOrder;
import cn.monitor4all.miaoshadao.entity.VisitorReservationTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

/**
 * 购票权益与访客预约履约数据访问接口。
 *
 * @author Qoder
 * @date 2026/09/20
 */
@Mapper
public interface FulfillmentMapper {

    int reserveStock(@Param("stockId") Integer stockId);
    Integer selectStockRemaining(@Param("stockId") Integer stockId);
    int insertOrder(FulfillmentTicketOrder order);
    int insertTask(VisitorReservationTask task);
    int insertOutbox(FulfillmentOutboxEvent event);
    FulfillmentTicketOrder selectOrder(@Param("orderNo") String orderNo);
    FulfillmentTicketOrder selectOrderByUserSlot(@Param("userId") Long userId, @Param("visitSlot") String visitSlot);
    VisitorReservationTask selectTask(@Param("orderNo") String orderNo);
    List<VisitorReservationTask> selectTasks(@Param("status") String status);
    List<FulfillmentOutboxEvent> selectDispatchableEvents(@Param("now") Date now, @Param("limit") Integer limit);
    int markEventSent(@Param("eventId") String eventId, @Param("now") Date now);
    int markEventConsumed(@Param("eventId") String eventId, @Param("now") Date now);
    int requeueEvent(@Param("eventId") String eventId, @Param("nextRetryTime") Date nextRetryTime,
                     @Param("now") Date now);
    int markEventManual(@Param("eventId") String eventId, @Param("now") Date now);
    int requeueEventByOrder(@Param("orderNo") String orderNo, @Param("nextRetryTime") Date nextRetryTime,
                            @Param("now") Date now);
    int markEventManualByOrder(@Param("orderNo") String orderNo, @Param("now") Date now);
    int claimTask(@Param("orderNo") String orderNo, @Param("now") Date now);
    int markTaskSuccess(@Param("orderNo") String orderNo, @Param("operator") String operator, @Param("now") Date now);
    int markTaskRetry(@Param("orderNo") String orderNo, @Param("retryCount") Integer retryCount,
                      @Param("nextRetryTime") Date nextRetryTime, @Param("errorCode") String errorCode,
                      @Param("errorMessage") String errorMessage, @Param("now") Date now);
    int markTaskManual(@Param("orderNo") String orderNo, @Param("retryCount") Integer retryCount,
                       @Param("errorCode") String errorCode, @Param("errorMessage") String errorMessage,
                       @Param("now") Date now);
    int markOrderFulfillment(@Param("orderNo") String orderNo, @Param("status") String status, @Param("now") Date now);
}
