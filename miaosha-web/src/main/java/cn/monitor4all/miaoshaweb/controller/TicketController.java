package cn.monitor4all.miaoshaweb.controller;

import cn.monitor4all.miaoshadao.model.ApiResponse;
import cn.monitor4all.miaoshadao.model.PurchaseRecord;
import cn.monitor4all.miaoshadao.model.PurchaseRequest;
import cn.monitor4all.miaoshadao.model.Ticket;
import cn.monitor4all.miaoshadao.model.UpdateTicketsRequest;
import cn.monitor4all.miaoshadao.model.CancelPurchaseRequest;
import cn.monitor4all.miaoshadao.model.CancelPurchaseResponse;
import cn.monitor4all.miaoshadao.dao.TicketOrder;
import cn.monitor4all.miaoshaservice.service.*;
import cn.monitor4all.miaoshadao.model.MiaoshaOperationResponse;
import cn.monitor4all.miaoshadao.model.MiaoshaStatusResponse;
import com.google.common.util.concurrent.RateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 票券控制器
 * 提供票券查询、购买、统计等相关接口
 */
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

    @Resource
    private ResponseTimeStatisticsService responseTimeStatisticsService;


    // Guava令牌桶：每秒放行10个请求
    RateLimiter rateLimiter = RateLimiter.create(10);
    
    // 获取最近3天的票券信息
    @GetMapping("/list")
    public ApiResponse<List<Ticket>> getRecentTickets() {
        long startTime = System.currentTimeMillis();
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
        } finally {
            long endTime = System.currentTimeMillis();
            LOGGER.info("获取最近3天票券信息接口响应时间: {}ms", endTime - startTime);
        }
    }

    // 获取最近3天的票券信息（包含用户购买状态）
    @GetMapping("/listWithUserStatus")
    public ApiResponse<List<Ticket>> getRecentTicketsWithUserStatus(@RequestParam(required = false) Long userId) {
        long startTime = System.currentTimeMillis();
        try {
            LOGGER.info("开始获取最近3天的票券信息（包含用户购买状态），用户ID: {}", userId);

            // 获取带用户购买状态的票券信息
            List<Ticket> tickets = ticketService.getRecentTicketsWithUserStatus(userId);

            // 如果缓存中没有数据，尝试从数据库加载
            if (tickets == null || tickets.isEmpty()) {
                LOGGER.info("缓存中没有票券数据，从数据库加载");
                ticketService.updateDailyTickets();
                // 直接获取票券信息，不调用不存在的方法
                tickets = ticketService.getRecentTicketsWithUserStatus(userId);
            }

            LOGGER.info("成功获取票券信息（包含用户购买状态），用户ID: {}, 共{}张票券", userId, tickets.size());
            return ApiResponse.success(tickets);
        } catch (Exception e) {
            LOGGER.error("获取票券列表（包含用户购买状态）失败: {}", e.getMessage(), e);
            return ApiResponse.error("获取票券列表失败: " + e.getMessage());
        } finally {
            long endTime = System.currentTimeMillis();
            LOGGER.info("获取最近3天票券信息（包含用户购买状态）接口响应时间: {}ms", endTime - startTime);
        }
    }
    
    /**
     * 验证抢购时间是否有效
     * @param date 票券日期
     * @return 验证结果
     */
    @GetMapping("/validateTime")
    public ApiResponse<Map<String, Object>> validatePurchaseTime(@RequestParam String date) {
        long startTime = System.currentTimeMillis();
        try {
            LOGGER.info("开始验证抢购时间，日期: {}", date);
            
            Map<String, Object> validationResult = ticketService.getPurchaseTime(date);
            
            LOGGER.info("抢购时间验证完成，日期: {}, 结果: {}", date, validationResult.get("valid"));
            return ApiResponse.success(validationResult);
        } catch (Exception e) {
            LOGGER.error("验证抢购时间失败，日期: {}", date, e);
            return ApiResponse.error("验证抢购时间失败: " + e.getMessage());
        } finally {
            long endTime = System.currentTimeMillis();
            LOGGER.info("验证抢购时间接口响应时间: {}ms", endTime - startTime);
        }
    }
    
    /**
     * 检查票数合法性 根据date实现
     * @param date 票券日期
     * @return 验证结果
     */
    @GetMapping("/validateTicketCount")
    public ApiResponse<Map<String, Object>> validateTicketCount(@RequestParam String date) {
        long startTime = System.currentTimeMillis();
        try {
            LOGGER.info("开始检查票数合法性，日期: {}", date);
            
            Map<String, Object> validationResult = ticketService.getTicketCount(date);
            
            LOGGER.info("票数合法性检查完成，日期: {}, 结果: {}", date, validationResult.get("valid"));
            return ApiResponse.success(validationResult);
        } catch (Exception e) {
            LOGGER.error("检查票数合法性失败，日期: {}", date, e);
            return ApiResponse.error("检查票数合法性失败: " + e.getMessage());
        } finally {
            long endTime = System.currentTimeMillis();
            LOGGER.info("检查票数合法性接口响应时间: {}ms", endTime - startTime);
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
    public ApiResponse<String> getVerifyHash(@RequestParam(value = "date") String date,
                                            @RequestParam(value = "userId") Long userId) {
        long startTime = System.currentTimeMillis();
        try {
            LOGGER.info("开始获取验证hash，用户ID: {}, 日期: {}", userId, date);
            
            // 参数验证
            if (userId == null) {
                LOGGER.warn("用户ID不能为空");
                return ApiResponse.error("用户ID不能为空");
            }
            
            if (date == null || date.trim().isEmpty()) {
                LOGGER.warn("日期参数不能为空");
                return ApiResponse.error("日期参数不能为空");
            }
            
            // 调用服务层获取验证hash
            String hash = userService.getVerifyHash4Ticket(date, userId);
            
            if (hash == null || hash.trim().isEmpty()) {
                LOGGER.warn("获取验证hash失败，hash值为空");
                return ApiResponse.error("获取验证hash失败");
            }
            
            LOGGER.info("成功获取验证hash，用户ID: {}, 日期: {}, hash: {}", userId, date, hash);
            return ApiResponse.success(hash);
            
        } catch (IllegalArgumentException e) {
            LOGGER.warn("获取验证hash参数错误: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        } catch (IllegalStateException e) {
            LOGGER.warn("获取验证hash业务错误: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            LOGGER.error("获取验证hash系统错误，用户ID: {}, 日期: {}", userId, date, e);
            return ApiResponse.error("系统错误，获取验证hash失败，请重试");
        } finally {
            long endTime = System.currentTimeMillis();
            LOGGER.info("获取验证hash接口响应时间: {}ms", endTime - startTime);
        }
    }


    /**
     * 一天票数设置1000
     *   用户请求 → 前置校验 → 限流检查 → 异步处理 → 返回结果
     *     ↓           ↓         ↓         ↓         ↓
     *   参数验证   验证码校验   用户限流   库存更新   成功/失败
     *     ↓           ↓         ↓         ↓         ↓
     *   时间验证   用户状态    接口限流   订单生成   异步通知
     *     ↓           ↓         ↓         ↓         ↓
     *   快速失败   快速失败    快速失败   缓存更新   监控记录
     *
     *   限流 → 合法性校验 → 参数校验 → 防重放 → 库存预扣 → 核心业务 → 结果返回
     *   核心原则："轻量拦截在前，重操作在后"，用最低的成本（限流、缓存校验）拦截尽可能多的无效请求，让真正有效的请求进入核心业务流程，从而在高并发场景下保证系统的稳定性和安全性。
     * @param request
     * @param httpRequest
     * @return  V1票券购买接口响应时间: 15ms
     *
     * 1000 吞吐量：950.5
     * 2000 吞吐量：589.275  响应时间：3-7ms  3579ms-5052ms
     * 总请求数: 2000, 平均响应时间: 1989.40 ms ms  吞吐量：484.9660523763337
     */
    @PostMapping("/v1/purchase")
    public ApiResponse<PurchaseRecord> purchaseTicket(@RequestBody PurchaseRequest request, HttpServletRequest httpRequest) {
        long startTime = System.currentTimeMillis();
        long responseTime = 0;
        try {
            LOGGER.info("V1开始处理票券购买请求，用户ID: {}, 日期: {}", request.getUserId(), request.getDate());

            // 调用服务层购买票券
            ApiResponse<PurchaseRecord> response = ticketService.purchaseTicket(request);
            
            LOGGER.info("票券购买成功，用户ID: {}, 日期: {}, 票券编号: {}",
                    request.getUserId(), request.getDate(), response.getData().getTicketCode());
            
            return response;
            
        } catch (IllegalArgumentException e) {
            LOGGER.warn("票券购买参数错误: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        } catch (IllegalStateException e) {
            LOGGER.warn("票券购买业务错误: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            LOGGER.error("票券购买系统错误: {}", e.getMessage(), e);
            return ApiResponse.error(e.getMessage());
        } finally {
            long endTime = System.currentTimeMillis();
            responseTime = endTime - startTime;
            LOGGER.info("V1票券购买接口响应时间: {}ms", responseTime);
            // 收集响应时间统计数据
            responseTimeStatisticsService.addResponseTime(responseTime);
        }
    }

    /**
     * 同步购买 乐观锁
     * @param request
     * @param httpRequest
     * @return V1乐观锁票券购买接口响应时间: 21ms
     * 2000 吞吐量：546.2988254575253  响应时间：3-7ms
     * 总请求数: 2000, 平均响应时间: 644.14 ms  吞吐量：844.9514152936206
     * 总请求数: 2000, 平均响应时间: 505.67 ms
     * 吞吐量：838.2229673093042
     */
    @PostMapping("/v1/purchase/optimistic")
    public ApiResponse<PurchaseRecord> purchaseTicketWithOptimistic(@RequestBody PurchaseRequest request, HttpServletRequest httpRequest) {
        long startTime = System.currentTimeMillis();
        long responseTime = 0;
        try {
            LOGGER.info("V1开始处理票券购买请求(乐观锁)，用户ID: {}, 日期: {}", request.getUserId(), request.getDate());
            
            // 调用服务层购买票券(乐观锁)
            ApiResponse<PurchaseRecord> response = ticketService.purchaseTicketV1WithOptimisticLock(request);
            
            LOGGER.info("票券购买成功(乐观锁)，用户ID: {}, 日期: {}, 票券编号: {}",
                    request.getUserId(), request.getDate(), response.getData().getTicketCode());
            
            return response;
            
        } catch (IllegalArgumentException e) {
            LOGGER.warn("票券购买参数错误: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        } catch (IllegalStateException e) {
            LOGGER.warn("票券购买业务错误: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            LOGGER.error("票券购买系统错误: {}", e.getMessage(), e);
            return ApiResponse.error(e.getMessage());
        } finally {
            long endTime = System.currentTimeMillis();
            responseTime = endTime - startTime;
            LOGGER.info("V1乐观锁票券购买接口响应时间: {}ms", responseTime);
            // 收集响应时间统计数据
            responseTimeStatisticsService.addResponseTime(responseTime);
        }
    }
    
    /**
     * V2异步抢购接口
     * @param request 抢购请求
     * @param httpRequest HTTP请求对象
     * @return 异步抢购结果
     * V2异步抢购接口响应时间: 10ms
     * 2000 吞吐量：1989.025 响应时间：3-7ms
     */
    @PostMapping("/v2/purchase/optimistic")
    public ApiResponse<Map<String, Object>> purchaseTicketAsync(@RequestBody PurchaseRequest request, HttpServletRequest httpRequest) {
        long startTime = System.currentTimeMillis();
        long responseTime = 0;
        try {
            LOGGER.info("V2开始处理异步抢购请求，用户ID: {}, 日期: {}", request.getUserId(), request.getDate());
            
            // 调用服务层异步购买票券
            ApiResponse<Map<String, Object>> result = ticketService.purchaseTicketV2(request);
            
            LOGGER.info("V2异步抢购请求处理完成，用户ID: {}, 日期: {}",
                    request.getUserId(), request.getDate());
            
            return result;
            
        } catch (IllegalArgumentException e) {
            LOGGER.warn("异步抢购参数错误: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        } catch (IllegalStateException e) {
            LOGGER.warn("异步抢购业务错误: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            LOGGER.error("异步抢购系统错误: {}", e.getMessage(), e);
            return ApiResponse.error("系统错误，请稍后重试");
        } finally {
            long endTime = System.currentTimeMillis();
            responseTime = endTime - startTime;
            LOGGER.info("V2异步抢购接口响应时间: {}ms", responseTime);
            // 收集响应时间统计数据
            responseTimeStatisticsService.addResponseTime(responseTime);
        }
    }
    
    /**
     * 查询异步抢购结果
     * @param requestId 请求ID
     * @param userId 用户ID
     * @param date 日期
     * @return 抢购结果
     */
    @GetMapping("/v2/purchaseResult")
    public ApiResponse<Map<String, Object>> getPurchaseResult(
            @RequestParam String requestId,
            @RequestParam Long userId,
            @RequestParam String date) {
        long startTime = System.currentTimeMillis();
        long responseTime = 0;
        try {
            LOGGER.info("查询异步抢购结果，请求ID: {}, 用户ID: {}, 日期: {}", requestId, userId, date);
            
            ApiResponse<Map<String, Object>> result = ticketService.getPurchaseResult(requestId, userId, date);
            
            LOGGER.info("异步抢购结果查询完成，请求ID: {}, 结果: {}", requestId, result.getData().get("status"));
            
            return result;
        } catch (Exception e) {
            LOGGER.error("查询异步抢购结果失败: {}", e.getMessage(), e);
            return ApiResponse.error("查询抢购结果失败: " + e.getMessage());
        } finally {
            long endTime = System.currentTimeMillis();
            LOGGER.info("查询异步抢购结果接口响应时间: {}ms", endTime - startTime);
        }
    }
    
    // 测试接口：手动更新票券数据
    @PostMapping("/admin/updateDailyTickets")
    public ApiResponse<String> updateDailyTickets() {
        long startTime = System.currentTimeMillis();
        try {
            LOGGER.info("手动执行每日票券更新任务");
            // 调用服务层的方法来更新票券
            ticketService.updateDailyTickets();
            return ApiResponse.success("票券更新任务执行成功");
        } catch (Exception e) {
            LOGGER.error("票券更新任务执行失败: {}", e.getMessage(), e);
            return ApiResponse.error("票券更新任务执行失败: " + e.getMessage());
        } finally {
            long endTime = System.currentTimeMillis();
            LOGGER.info("手动更新票券数据接口响应时间: {}ms", endTime - startTime);
        }
    }
    
    /**
     * 获取票券统计信息
     * @return 票券统计信息
     */
    @GetMapping("/stats")
    public ApiResponse<Object> getTicketStats() {
        long startTime = System.currentTimeMillis();
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
        } finally {
            long endTime = System.currentTimeMillis();
            LOGGER.info("获取票券统计信息接口响应时间: {}ms", endTime - startTime);
        }
    }


    /**
     * 管理员接口：使用悲观锁修改3天票数（不影响用户抢票）
     * @param request 修改请求
     * @return 修改结果
     */
    @PostMapping("/admin/updateTicketsWithPessimistic")
    public ApiResponse<Map<String, Object>> updateTicketsWithPessimistic(@RequestBody UpdateTicketsRequest request) {
        long startTime = System.currentTimeMillis();
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
        } finally {
            long endTime = System.currentTimeMillis();
            LOGGER.info("管理员修改3天票数接口响应时间: {}ms", endTime - startTime);
        }
    }
    
    /**
     * 管理员接口：暂停秒杀活动
     * @return 暂停结果
     */
    @PostMapping("/admin/pauseMiaosha")
    public ApiResponse<MiaoshaOperationResponse> pauseMiaosha() {
        long startTime = System.currentTimeMillis();
        try {
            LOGGER.info("管理员请求暂停秒杀活动");
            
            boolean success = miaoshaStatusService.pauseMiaosha();
            
            if (success) {
                MiaoshaOperationResponse result = new MiaoshaOperationResponse("SUCCESS", "秒杀活动已暂停");
                
                LOGGER.info("秒杀活动暂停成功");
                return ApiResponse.success(result);
            } else {
                return ApiResponse.error("暂停秒杀活动失败");
            }
            
        } catch (Exception e) {
            LOGGER.error("暂停秒杀活动异常: {}", e.getMessage(), e);
            return ApiResponse.error("暂停秒杀活动异常: " + e.getMessage());
        } finally {
            long endTime = System.currentTimeMillis();
            LOGGER.info("暂停秒杀活动接口响应时间: {}ms", endTime - startTime);
        }
    }
    
    /**
     * 管理员接口：恢复秒杀活动
     * @return 恢复结果
     */
    @PostMapping("/admin/resumeMiaosha")
    public ApiResponse<MiaoshaOperationResponse> resumeMiaosha() {
        long startTime = System.currentTimeMillis();
        try {
            LOGGER.info("管理员请求恢复秒杀活动");
            
            boolean success = miaoshaStatusService.resumeMiaosha();
            
            if (success) {
                MiaoshaOperationResponse result = new MiaoshaOperationResponse("SUCCESS", "秒杀活动已恢复");
                
                LOGGER.info("秒杀活动恢复成功");
                return ApiResponse.success(result);
            } else {
                return ApiResponse.error("恢复秒杀活动失败");
            }
            
        } catch (Exception e) {
            LOGGER.error("恢复秒杀活动异常: {}", e.getMessage(), e);
            return ApiResponse.error("恢复秒杀活动异常: " + e.getMessage());
        } finally {
            long endTime = System.currentTimeMillis();
            LOGGER.info("恢复秒杀活动接口响应时间: {}ms", endTime - startTime);
        }
    }
    
    /**
     * 获取秒杀活动状态
     * @return 秒杀活动状态
     */
    @GetMapping("/miaosha/status")
    public ApiResponse<MiaoshaStatusResponse> getMiaoshaStatus() {
        long startTime = System.currentTimeMillis();
        try {
            LOGGER.info("获取秒杀活动状态");
            
            MiaoshaStatusService.MiaoshaStatus status = miaoshaStatusService.getMiaoshaStatusDetail();
            
            MiaoshaStatusResponse result = new MiaoshaStatusResponse();
            result.setPaused(status.isPaused());
            result.setStatus(status.getStatus());
            result.setPauseTime(status.getPauseTime());
            result.setResumeTime(status.getResumeTime());
            result.setOperator(status.getOperator());
            result.setReason(status.getReason());
            result.setTimestamp(System.currentTimeMillis());
            
            return ApiResponse.success(result);
            
        } catch (Exception e) {
            LOGGER.error("获取秒杀活动状态失败: {}", e.getMessage(), e);
            return ApiResponse.error("获取秒杀活动状态失败: " + e.getMessage());
        } finally {
            long endTime = System.currentTimeMillis();
            LOGGER.info("获取秒杀活动状态接口响应时间: {}ms", endTime - startTime);
        }
    }
    
    /**
     * 管理员接口：使用乐观锁无感知修改票券库存
     * @param request 修改请求
     * @return 修改结果
     */
    @PostMapping("/admin/updateTicketsWithOptimistic")
    public ApiResponse<Map<String, Object>> updateTicketsWithOptimistic(@RequestBody UpdateTicketsRequest request) {
        long startTime = System.currentTimeMillis();
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
        } finally {
            long endTime = System.currentTimeMillis();
            LOGGER.info("乐观锁无感知修改票券库存接口响应时间: {}ms", endTime - startTime);
        }
    }
    
    /**
     * 获取乐观锁修改重试统计信息
     * @return 重试统计信息
     */
    @GetMapping("/admin/optimisticRetryStats")
    public ApiResponse<Object> getOptimisticRetryStats() {
        long startTime = System.currentTimeMillis();
        try {
            LOGGER.info("获取乐观锁修改重试统计信息");
            
            Map<String, Object> stats = ticketOptimisticUpdateService.getRetryStatistics();
            
            return ApiResponse.success(stats);
            
        } catch (Exception e) {
            LOGGER.error("获取乐观锁修改重试统计信息失败: {}", e.getMessage(), e);
            return ApiResponse.error("获取重试统计信息失败: " + e.getMessage());
        } finally {
            long endTime = System.currentTimeMillis();
            LOGGER.info("获取乐观锁修改重试统计信息接口响应时间: {}ms", endTime - startTime);
        }
    }


    /**
     * TODO 取消购票接口
     * 1. 验证取消条件
     * 2. 恢复票券库存
     * 3. 更新订单状态
     * 4. 更新相关缓存
     * @param request 取消购票请求
     * @param httpRequest HTTP请求对象
     * @return 取消购票结果
     */
    @PostMapping("/v1/cancel")
    public ApiResponse<CancelPurchaseResponse> cancelPurchase(@RequestBody CancelPurchaseRequest request, HttpServletRequest httpRequest) {
        long startTime = System.currentTimeMillis();
        try {
            LOGGER.info("开始处理票券取消请求，用户ID: {}, 订单号: {}, 票券编码: {}", 
                       request.getUserId(), request.getOrderNo(), request.getTicketCode());

            // 调用服务层取消购票
            CancelPurchaseResponse response = ticketService.cancelPurchase(request);
            
            LOGGER.info("票券取消成功，用户ID: {}, 订单号: {}, 票券编码: {}",
                       request.getUserId(), request.getOrderNo(), request.getTicketCode());
            
            return ApiResponse.success(response);
            
        } catch (IllegalArgumentException e) {
            LOGGER.warn("票券取消参数错误: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        } catch (IllegalStateException e) {
            LOGGER.warn("票券取消业务错误: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            LOGGER.error("票券取消系统错误: {}", e.getMessage(), e);
            return ApiResponse.error("系统错误，取消失败，请重试");
        } finally {
            long endTime = System.currentTimeMillis();
            LOGGER.info("票券取消接口响应时间: {}ms", endTime - startTime);
        }
    }

    @GetMapping("/v1/order/list")
    public ApiResponse<List<TicketOrder>> getOrderList(@RequestParam Long userId) {
        return ticketService.getOrdersByUserId(userId);
    }

    @GetMapping("/v1/order/detail")
    public ApiResponse<TicketOrder> getOrderDetail(@RequestParam Long orderId) {
        return ticketService.getOrderById(orderId);
    }

    /**
     * 获取响应时间统计信息
     * @return 统计信息
     */
    @GetMapping("/statistics/responseTime")
    public ApiResponse<String> getResponseTimeStatistics() {
        try {
            String statistics = responseTimeStatisticsService.getStatistics();
            LOGGER.info("获取响应时间统计信息: {}", statistics);
            return ApiResponse.success(statistics);
        } catch (Exception e) {
            LOGGER.error("获取响应时间统计信息失败: {}", e.getMessage(), e);
            return ApiResponse.error("获取统计信息失败: " + e.getMessage());
        }
    }

    /**
     * 清空响应时间统计信息
     * @return 操作结果
     */
    @GetMapping("/statistics/responseTime/clear")
    public ApiResponse<String> clearResponseTimeStatistics() {
        try {
            responseTimeStatisticsService.clearStatistics();
            LOGGER.info("清空响应时间统计信息成功");
            return ApiResponse.success("统计信息已清空");
        } catch (Exception e) {
            LOGGER.error("清空响应时间统计信息失败: {}", e.getMessage(), e);
            return ApiResponse.error("清空统计信息失败: " + e.getMessage());
        }
    }
}