package cn.monitor4all.miaoshaweb.controller;

import cn.monitor4all.miaoshadao.dao.User;
import cn.monitor4all.miaoshadao.model.ApiResponse;
import cn.monitor4all.miaoshadao.model.PurchaseRecord;
import cn.monitor4all.miaoshadao.model.PurchaseRequest;
import cn.monitor4all.miaoshadao.model.Ticket;
import cn.monitor4all.miaoshadao.model.UpdateTicketsRequest;
import cn.monitor4all.miaoshaservice.service.MiaoshaStatusService;
import cn.monitor4all.miaoshaservice.service.TicketOptimisticUpdateService;
import cn.monitor4all.miaoshaservice.service.TicketService;
import cn.monitor4all.miaoshaservice.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@RestController
@RequestMapping("/api/tickets")
@CrossOrigin // 允许所有来源访问，解决403 Forbidden错误
public class TicketController {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(TicketController.class);
    
    @Resource
    private TicketService ticketService;
    
    @Resource
    private MiaoshaStatusService miaoshaStatusService;
    
    @Resource
    private TicketOptimisticUpdateService ticketOptimisticUpdateService;

    @Resource
    private UserService userService;
    
    // 获取最近3天的票券信息
    @GetMapping("/list")
    public ApiResponse<List<Ticket>> getRecentTickets() {
        try {
            LOGGER.info("开始获取最近3天的票券信息");
            
            // 从数据库获取票券信息
            List<Ticket> tickets = ticketService.getRecentTickets();
            
            // 如果缓存中没有数据，尝试从数据库加载
            if (tickets == null || tickets.isEmpty()) {
                LOGGER.info("缓存中没有票券数据，从数据库加载");
                ticketService.updateDailyTickets();
                tickets = ticketService.getRecentTickets();
            }
            
            LOGGER.info("成功获取票券信息，共{}张票券", tickets.size());
            return ApiResponse.success(tickets);
        } catch (Exception e) {
            LOGGER.error("获取票券列表失败: {}", e.getMessage(), e);
            return ApiResponse.error("获取票券列表失败: " + e.getMessage());
        }
    }
    
    /**
     * 验证抢购时间是否有效
     * @param date 票券日期
     * @return 验证结果
     */
    @GetMapping("/validateTime")
    public ApiResponse<Map<String, Object>> validatePurchaseTime(@RequestParam String date) {
        try {
            LOGGER.info("开始验证抢购时间，日期: {}", date);
            
            Map<String, Object> validationResult = ticketService.getPurchaseTime(date);
            
            LOGGER.info("抢购时间验证完成，日期: {}, 结果: {}", date, validationResult.get("valid"));
            return ApiResponse.success(validationResult);
        } catch (Exception e) {
            LOGGER.error("验证抢购时间失败，日期: {}", date, e);
            return ApiResponse.error("验证抢购时间失败: " + e.getMessage());
        }
    }
    
    /**
     * 检查票数合法性 根据date实现
     * @param date 票券日期
     * @return 验证结果
     */
    @GetMapping("/validateTicketCount")
    public ApiResponse<Map<String, Object>> validateTicketCount(@RequestParam String date) {
        try {
            LOGGER.info("开始检查票数合法性，日期: {}", date);
            
            Map<String, Object> validationResult = ticketService.getTicketCount(date);
            
            LOGGER.info("票数合法性检查完成，日期: {}, 结果: {}", date, validationResult.get("valid"));
            return ApiResponse.success(validationResult);
        } catch (Exception e) {
            LOGGER.error("检查票数合法性失败，日期: {}", date, e);
            return ApiResponse.error("检查票数合法性失败: " + e.getMessage());
        }
    }

    /**
     * 验证接口：下单前用户获取验证值
     * @return
     * 测试：500张票 3000个线程，循环10次
     * 产生 500 个订单，售卖了 500个，3次测试，都卖完
     * 其余部分数据被限流，或者购买失败
     * 吞吐量 1916 1816 1741 每秒
     */
    @RequestMapping(value = "/getVerifyHash", method = {RequestMethod.GET})
    @ResponseBody
    public String getVerifyHash(@RequestParam(value = "date") String date,
                                @RequestParam(value = "userId") Long userId) {
        String hash;
        try {
            hash = userService.getVerifyHash4Ticket(date, userId);
        } catch (Exception e) {
            LOGGER.error("获取验证hash失败，原因：[{}]", e.getMessage());
            return "获取验证hash失败";
        }
        return String.format("请求抢购验证hash值为：%s", hash);
    }


    /**
     *   用户请求 → 前置校验 → 限流检查 → 异步处理 → 返回结果
     *     ↓           ↓         ↓         ↓         ↓
     *   参数验证   验证码校验   用户限流   库存更新   成功/失败
     *     ↓           ↓         ↓         ↓         ↓
     *   时间验证   用户状态    接口限流   订单生成   异步通知
     *     ↓           ↓         ↓         ↓         ↓
     *   快速失败   快速失败    快速失败   缓存更新   监控记录
     * @param request
     * @param httpRequest
     * @return
     */
    @PostMapping("/v1/purchase")
    public ApiResponse<PurchaseRecord> purchaseTicket(@RequestBody PurchaseRequest request, HttpServletRequest httpRequest) {
        try {
            LOGGER.info("开始处理票券购买请求，用户ID: {}, 日期: {}", request.getUserId(), request.getDate());

            // 用户访问票数页面，生成MD5字段
            // 抢购，携带MD5字段

            // 1、用户限流
            // 参数、验证码 校验
            // 验证是否在抢购时间内
            // 接口限流
            // 3、用户、票数校验
            // 4、乐观锁更新票券库存
            // 5、生成订单
            // 6、返回选购成功
            // 7、查询订单


            int count = userService.addUserCount(request.getUserId());
            LOGGER.info("用户截至该次的访问次数为: [{}]", count);
            boolean isBanned = userService.getUserIsBanned(request.getUserId());
            if (isBanned) {
                return ApiResponse.error("购买失败，超过频率限制");
            }
            
            // 参数验证
            String purchaseDate = request.getDate();
            if (purchaseDate == null || purchaseDate.isEmpty()) {
                LOGGER.warn("日期格式错误，用户ID: {}, 日期: {}", request.getUserId(), request.getDate());
                return ApiResponse.error("日期格式错误或日期不能为空");
            }
            
            // 使用前端传递的userId，允许匿名用户
            Long userId = request.getUserId();
            if (Objects.isNull(userId)) {
                userId = User.ANONYMOUS; // 默认匿名用户
                LOGGER.info("用户ID为空，使用默认匿名用户ID: {}", userId);
            }
            
            // 调用服务层购买票券
            PurchaseRecord record = ticketService.purchaseTicket(String.valueOf(userId), purchaseDate);
            
            LOGGER.info("票券购买成功，用户ID: {}, 日期: {}, 票券编号: {}", 
                userId, purchaseDate, record.getTicketCode());
            
            return ApiResponse.success(record);
            
        } catch (IllegalArgumentException e) {
            LOGGER.warn("票券购买参数错误: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        } catch (IllegalStateException e) {
            LOGGER.warn("票券购买业务错误: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            LOGGER.error("票券购买系统错误: {}", e.getMessage(), e);
            return ApiResponse.error("系统错误，购买失败，请重试");
        }
    }

    @PostMapping("/v2/purchase")
    public ApiResponse<PurchaseRecord> purchaseTicketV2(@RequestBody PurchaseRequest request, HttpServletRequest httpRequest) {
        try {
            LOGGER.info("开始处理票券购买请求，用户ID: {}, 日期: {}", request.getUserId(), request.getDate());

            // 1. 请求参数校验

            // 前置校验

            // 限流检查

            // 异步处理

            // 返回结果

            // 调用服务层购买票券
            PurchaseRecord record = ticketService.purchaseTicketV2(request);

//            // 2. 用户限流检查
//            if (isUserRateLimited(request.getUserId())) {
//                return ApiResponse.error("用户访问频率过高，请稍后再试");
//            }
//
//            // 3. 接口限流检查
//            if (isApiRateLimited()) {
//                return ApiResponse.error("系统繁忙，请稍后再试");
//            }
//
//            // 4. 业务逻辑处理（异步）
//            CompletableFuture<PurchaseRecord> future = processPurchaseAsync(request);
//
//            // 5. 等待结果（设置超时）
//            PurchaseRecord record = future.get(5, TimeUnit.SECONDS);
//
//            LOGGER.info("票券购买成功，用户ID: {}, 日期: {}, 票券编号: {}",
//                    request.getUserId(), request.getDate(), record.getTicketCode());

            return ApiResponse.success(record);

        } catch (TimeoutException e) {
            LOGGER.warn("票券购买超时，用户ID: {}, 日期: {}", request.getUserId(), request.getDate());
            return ApiResponse.error("购买处理超时，请稍后查询订单状态");
        } catch (Exception e) {
            LOGGER.error("票券购买失败，用户ID: {}, 日期: {}", request.getUserId(), request.getDate(), e);
            return ApiResponse.error("购买失败，请重试");
        }
    }
    
    // 测试接口：手动更新票券数据
    @PostMapping("/admin/updateDailyTickets")
    public ApiResponse<String> updateDailyTickets() {
        try {
            LOGGER.info("手动执行每日票券更新任务");
            // 调用服务层的方法来更新票券
            ticketService.updateDailyTickets();
            return ApiResponse.success("票券更新任务执行成功");
        } catch (Exception e) {
            LOGGER.error("票券更新任务执行失败: {}", e.getMessage(), e);
            return ApiResponse.error("票券更新任务执行失败: " + e.getMessage());
        }
    }
    
    // 测试接口：获取票券统计信息
    @GetMapping("/stats")
    public ApiResponse<Object> getTicketStats() {
        try {
            LOGGER.info("获取票券统计信息");
            List<Ticket> tickets = ticketService.getRecentTickets();
            
            // 计算统计信息
            int totalTickets = tickets.stream().mapToInt(Ticket::getTotal).sum();
            int remainingTickets = tickets.stream().mapToInt(Ticket::getRemaining).sum();
            int soldTickets = totalTickets - remainingTickets;
            double sellRate = totalTickets > 0 ? (double) soldTickets / totalTickets * 100 : 0;
            
            Map<String, Object> stats = new HashMap<>();
            stats.put("totalTickets", totalTickets);
            stats.put("remainingTickets", remainingTickets);
            stats.put("soldTickets", soldTickets);
            stats.put("sellRate", Math.round(sellRate * 100.0) / 100.0);
            stats.put("tickets", tickets);
            
            return ApiResponse.success(stats);
        } catch (Exception e) {
            LOGGER.error("获取票券统计信息失败: {}", e.getMessage(), e);
            return ApiResponse.error("获取票券统计信息失败: " + e.getMessage());
        }
    }


    /**
     * 管理员接口：使用悲观锁修改3天票数（不影响用户抢票）
     * @param request 修改请求
     * @return 修改结果
     */
    @PostMapping("/admin/updateTicketsWithPessimistic")
    public ApiResponse<Map<String, Object>> updateTicketsWithPessimistic(@RequestBody UpdateTicketsRequest request) {
        try {
            LOGGER.info("管理员请求修改3天票数，请求参数: {}", request);
            
            // 参数验证
            if (request == null || request.getTicketUpdates() == null || request.getTicketUpdates().isEmpty()) {
                return ApiResponse.error("请求参数不能为空");
            }
            
            // 验证管理员权限（这里简化处理，实际应该验证JWT token或session）
            if (!"admin".equals(request.getAdminId())) {
                return ApiResponse.error("权限不足，需要管理员权限");
            }
            
            // 调用服务层方法，使用悲观锁批量更新票数
            Map<String, Object> result = ticketService.updateTicketsWithPessimistic(request.getTicketUpdates());
            
            LOGGER.info("管理员修改3天票数成功，结果: {}", result);
            return ApiResponse.success(result);
            
        } catch (Exception e) {
            LOGGER.error("管理员修改3天票数失败: {}", e.getMessage(), e);
            return ApiResponse.error("修改失败: " + e.getMessage());
        }
    }
    
    /**
     * 管理员接口：暂停秒杀活动
     * @return 暂停结果
     */
    @PostMapping("/admin/pauseMiaosha")
    public ApiResponse<Map<String, Object>> pauseMiaosha() {
        try {
            LOGGER.info("管理员请求暂停秒杀活动");
            
            boolean success = miaoshaStatusService.pauseMiaosha();
            
            if (success) {
                Map<String, Object> result = new HashMap<>();
                result.put("status", "SUCCESS");
                result.put("message", "秒杀活动已暂停");
                result.put("timestamp", System.currentTimeMillis());
                
                LOGGER.info("秒杀活动暂停成功");
                return ApiResponse.success(result);
            } else {
                return ApiResponse.error("暂停秒杀活动失败");
            }
            
        } catch (Exception e) {
            LOGGER.error("暂停秒杀活动异常: {}", e.getMessage(), e);
            return ApiResponse.error("暂停秒杀活动异常: " + e.getMessage());
        }
    }
    
    /**
     * 管理员接口：恢复秒杀活动
     * @return 恢复结果
     */
    @PostMapping("/admin/resumeMiaosha")
    public ApiResponse<Map<String, Object>> resumeMiaosha() {
        try {
            LOGGER.info("管理员请求恢复秒杀活动");
            
            boolean success = miaoshaStatusService.resumeMiaosha();
            
            if (success) {
                Map<String, Object> result = new HashMap<>();
                result.put("status", "SUCCESS");
                result.put("message", "秒杀活动已恢复");
                result.put("timestamp", System.currentTimeMillis());
                
                LOGGER.info("秒杀活动恢复成功");
                return ApiResponse.success(result);
            } else {
                return ApiResponse.error("恢复秒杀活动失败");
            }
            
        } catch (Exception e) {
            LOGGER.error("恢复秒杀活动异常: {}", e.getMessage(), e);
            return ApiResponse.error("恢复秒杀活动异常: " + e.getMessage());
        }
    }
    
    /**
     * 获取秒杀活动状态
     * @return 秒杀活动状态
     */
    @GetMapping("/miaosha/status")
    public ApiResponse<Object> getMiaoshaStatus() {
        try {
            LOGGER.info("获取秒杀活动状态");
            
            MiaoshaStatusService.MiaoshaStatus status = miaoshaStatusService.getMiaoshaStatusDetail();
            
            Map<String, Object> result = new HashMap<>();
            result.put("paused", status.isPaused());
            result.put("status", status.getStatus());
            result.put("pauseTime", status.getPauseTime());
            result.put("resumeTime", status.getResumeTime());
            result.put("operator", status.getOperator());
            result.put("reason", status.getReason());
            result.put("timestamp", System.currentTimeMillis());
            
            return ApiResponse.success(result);
            
        } catch (Exception e) {
            LOGGER.error("获取秒杀活动状态失败: {}", e.getMessage(), e);
            return ApiResponse.error("获取秒杀活动状态失败: " + e.getMessage());
        }
    }
    
    /**
     * 管理员接口：使用乐观锁无感知修改票券库存
     * @param request 修改请求
     * @return 修改结果
     */
    @PostMapping("/admin/updateTicketsWithOptimistic")
    public ApiResponse<Map<String, Object>> updateTicketsWithOptimistic(@RequestBody UpdateTicketsRequest request) {
        try {
            LOGGER.info("管理员请求使用乐观锁无感知修改票券库存，请求参数: {}", request);
            
            // 参数验证
            if (request == null || request.getTicketUpdates() == null || request.getTicketUpdates().isEmpty()) {
                return ApiResponse.error("请求参数不能为空");
            }
            
            // 验证管理员权限（这里简化处理，实际应该验证JWT token或session）
            if (!"admin".equals(request.getAdminId())) {
                return ApiResponse.error("权限不足，需要管理员权限");
            }
            
            // 调用服务层方法，使用乐观锁无感知更新票数
            Map<String, Object> result = ticketOptimisticUpdateService.updateTicketsWithOptimistic(request.getTicketUpdates());
            
            LOGGER.info("乐观锁无感知修改票券库存成功，结果: {}", result);
            return ApiResponse.success(result);
            
        } catch (Exception e) {
            LOGGER.error("乐观锁无感知修改票券库存失败: {}", e.getMessage(), e);
            return ApiResponse.error("修改失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取乐观锁修改重试统计信息
     * @return 重试统计信息
     */
    @GetMapping("/admin/optimisticRetryStats")
    public ApiResponse<Object> getOptimisticRetryStats() {
        try {
            LOGGER.info("获取乐观锁修改重试统计信息");
            
            Map<String, Object> stats = ticketOptimisticUpdateService.getRetryStatistics();
            
            return ApiResponse.success(stats);
            
        } catch (Exception e) {
            LOGGER.error("获取乐观锁修改重试统计信息失败: {}", e.getMessage(), e);
            return ApiResponse.error("获取重试统计信息失败: " + e.getMessage());
        }
    }
    
}