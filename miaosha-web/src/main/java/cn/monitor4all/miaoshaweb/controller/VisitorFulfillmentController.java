package cn.monitor4all.miaoshaweb.controller;

import cn.monitor4all.miaoshadao.entity.VisitorReservationTask;
import cn.monitor4all.miaoshadao.model.ApiResponse;
import cn.monitor4all.miaoshaservice.model.FulfillmentStatusView;
import cn.monitor4all.miaoshaservice.service.VisitorFulfillmentService;
import cn.monitor4all.miaoshaweb.model.FulfillmentPurchaseRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.List;

/**
 * 购票权益与访客预约履约接口。
 *
 * @author Qoder
 * @date 2026/09/20
 */
@RestController
@RequestMapping("/api/fulfillment")
public class VisitorFulfillmentController {

    @Resource
    private VisitorFulfillmentService visitorFulfillmentService;

    @PostMapping("/tickets")
    public ApiResponse<FulfillmentStatusView> purchase(@Valid @RequestBody FulfillmentPurchaseRequest request) {
        return ApiResponse.success(visitorFulfillmentService.purchaseTicket(request.getUserId(), request.getVisitSlot()));
    }

    @GetMapping("/tickets/status")
    public ApiResponse<FulfillmentStatusView> getStatus(@RequestParam Long userId, @RequestParam String visitSlot) {
        FulfillmentStatusView status = visitorFulfillmentService.getStatus(userId, visitSlot);
        if (status == null) {
            return ApiResponse.error(404, "未找到该场次的购票记录");
        }
        return ApiResponse.success(status);
    }

    @GetMapping("/quota")
    public ApiResponse<Integer> getQuota() {
        return ApiResponse.success(visitorFulfillmentService.getRemainingQuota());
    }

    @GetMapping("/ops/tasks")
    public ApiResponse<List<VisitorReservationTask>> listTasks(@RequestParam(required = false) String status) {
        return ApiResponse.success(visitorFulfillmentService.listTasks(status));
    }

    @PostMapping("/ops/tasks/{orderNo}/retry")
    public ApiResponse<Void> retry(@PathVariable String orderNo) {
        visitorFulfillmentService.retryManually(orderNo);
        return ApiResponse.success(null);
    }

    @PostMapping("/ops/tasks/{orderNo}/complete")
    public ApiResponse<Void> complete(@PathVariable String orderNo, @RequestParam String operator) {
        visitorFulfillmentService.completeManually(orderNo, operator);
        return ApiResponse.success(null);
    }
}
