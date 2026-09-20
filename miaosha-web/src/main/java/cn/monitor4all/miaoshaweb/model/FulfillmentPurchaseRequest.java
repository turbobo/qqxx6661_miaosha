package cn.monitor4all.miaoshaweb.model;

import javax.validation.constraints.NotNull;

/**
 * 用户购票请求。
 *
 * @author Qoder
 * @date 2026/09/20
 */
public class FulfillmentPurchaseRequest {

    @NotNull(message = "预约人编号不能为空")
    private Long userId;

    @NotNull(message = "参观场次不能为空")
    private String visitSlot;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getVisitSlot() { return visitSlot; }
    public void setVisitSlot(String visitSlot) { this.visitSlot = visitSlot; }
}
