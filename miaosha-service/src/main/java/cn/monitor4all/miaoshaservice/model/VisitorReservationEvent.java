package cn.monitor4all.miaoshaservice.model;

/**
 * 访客预约请求事件。
 *
 * @author Qoder
 * @date 2026/09/20
 */
public class VisitorReservationEvent {

    private String eventId;
    private String orderNo;

    public VisitorReservationEvent() {
    }

    public VisitorReservationEvent(String eventId, String orderNo) {
        this.eventId = eventId;
        this.orderNo = orderNo;
    }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
}
