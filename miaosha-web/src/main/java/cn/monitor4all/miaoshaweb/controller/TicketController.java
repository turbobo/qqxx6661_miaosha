package cn.monitor4all.miaoshaweb.controller;

import cn.monitor4all.miaoshadao.model.ApiResponse;
import cn.monitor4all.miaoshadao.model.PurchaseRecord;
import cn.monitor4all.miaoshadao.model.PurchaseRequest;
import cn.monitor4all.miaoshadao.model.Ticket;
import cn.monitor4all.miaoshaservice.service.TicketService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tickets")
@CrossOrigin // 允许所有来源访问，解决403 Forbidden错误
public class TicketController {
    @Autowired
    private TicketService ticketService;
    
    // 获取最近3天的票券信息
    @GetMapping
    public ApiResponse<List<Ticket>> getRecentTickets() {
        List<Ticket> tickets = ticketService.getRecentTickets();
        return ApiResponse.success(tickets);
    }
    
    // 购买票券
    @PostMapping("/purchase")
    public ApiResponse<PurchaseRecord> purchaseTicket(@RequestBody PurchaseRequest request) {
        if (request.getUserId() == null || request.getDate() == null) {
            return ApiResponse.error("用户ID和日期不能为空");
        }
        
        // 检查是否已经购买
        if (ticketService.hasPurchased(request.getUserId(), request.getDate())) {
            return ApiResponse.error("您已购买过当天的票券，每人每天限购一张");
        }
        
        PurchaseRecord record = ticketService.purchaseTicket(request.getUserId(), request.getDate());
        if (record == null) {
            return ApiResponse.error("票券已售罄或购买失败");
        }
        
        return ApiResponse.success(record);
    }
}