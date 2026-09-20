package cn.monitor4all.miaoshaservice.service;

import cn.monitor4all.miaoshadao.entity.VisitorReservationTask;
import cn.monitor4all.miaoshaservice.model.FulfillmentStatusView;
import cn.monitor4all.miaoshaservice.model.VisitorReservationEvent;

import java.util.List;

/**
 * 购票权益独立成立后的访客预约履约服务。
 *
 * @author Qoder
 * @date 2026/09/20
 */
public interface VisitorFulfillmentService {

    FulfillmentStatusView purchaseTicket(Long userId, String visitSlot);
    FulfillmentStatusView getStatus(Long userId, String visitSlot);
    Integer getRemainingQuota();
    void processVisitorReservation(VisitorReservationEvent event);
    List<VisitorReservationTask> listTasks(String status);
    void retryManually(String orderNo);
    void completeManually(String orderNo, String operator);
}
