package cn.monitor4all.miaoshaservice.service;

import cn.monitor4all.miaoshadao.dao.TicketEntity;
import cn.monitor4all.miaoshadao.dao.TicketOrder;
import cn.monitor4all.miaoshadao.dao.TicketPurchaseRecord;
import cn.monitor4all.miaoshadao.model.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 票券服务接口
 */
public interface TicketService {
    
    /**
     * 获取最近3天的票券信息
     * @return 票券列表
     */
    List<Ticket> getRecentTickets();

    /**
     * 获取最近3天的票券信息，携带用户是否抢购该票数的结果
     * @param userId 用户ID，如果为null则返回基础票券信息
     * @return 票券列表
     */
    List<Ticket> getRecentTicketsWithUserStatus(Long userId);
    
    /**
     * 购买票券
     * @param request 购票请求
     * @return 购买记录，如果购买失败返回null
     */
    ApiResponse<PurchaseRecord> purchaseTicket(PurchaseRequest request) throws Exception;


    ApiResponse<PurchaseRecord> purchaseTicketV1WithOptimisticLock(PurchaseRequest request) throws Exception;

    PurchaseRecord asyncPurchaseTicketWithOptimisticLock(PurchaseRequest request) throws Exception;


    ApiResponse<Map<String, Object>> purchaseTicketV2(PurchaseRequest request);

    /**
     * 检查用户是否已购买指定日期的票券
     * @param userId 用户ID
     * @param date 日期
     * @return 是否已购买
     */
    boolean hasPurchased(Long userId, String date);
    
    /**
     * 验证抢购时间是否有效
     * @param date 票券日期
     * @return 验证结果，包含是否有效和详细信息
     */
    Map<String, Object> getPurchaseTime(String date);

    /**
     * 验证抢购时间是否有效
     * @param date 票券日期
     * @return 验证结果，包含是否有效和详细信息
     */
    void validatePurchaseTime(String date);
    
    /**
     * 检查票数合法性 根据date实现
     * @param date 票券日期
     * @return 验证结果，包含是否有效和详细信息
     */
    Map<String, Object> getTicketCount(String date);

    void validateTicketCount(String date);

    
    /**
     * 获取票券数据库实体
     * @param date 日期
     * @return 票券实体
     */
    TicketEntity getTicketEntityByDate(String date);
    
    /**
     * 获取票券数据库实体（悲观锁）
     * @param date 日期
     * @return 票券实体
     */
    TicketEntity getTicketEntityByDateForUpdate(String date);
    
    /**
     * 更新票券库存（乐观锁）
     * @param ticketEntity 票券实体
     * @return 是否更新成功
     */
    boolean updateTicketStockByOptimistic(TicketEntity ticketEntity);
    
    /**
     * 保存购买记录
     * @param purchaseRecord 购买记录
     * @return 是否保存成功
     */
    boolean savePurchaseRecord(TicketPurchaseRecord purchaseRecord);
    
    /**
     * 每天0点更新票券数据
     */
    void updateDailyTickets();

    /**
     * 管理员使用悲观锁批量修改票数（不影响用户抢票）
     * @param ticketUpdates 票数修改列表
     * @return 修改结果
     */
    Map<String, Object> updateTicketsWithPessimistic(List<UpdateTicketsRequest.TicketUpdate> ticketUpdates);
    
    /**
     * 使用悲观锁扣库存购票（包含事务控制）
     * @param request 购票请求
     * @return 购票结果
     * @throws Exception 购票异常
     */
    ApiResponse<PurchaseRecord> purchaseTicketWithPessimisticLock(PurchaseRequest request) throws Exception;
    
    /**
     * 使用悲观锁购票并生成订单（新方法）
     * 1. 使用SELECT FOR UPDATE锁住票券记录
     * 2. 事务控制
     * 3. 扣减库存
     * 4. 生成ticket_order订单
     * @param request 购票请求
     * @return 购票结果
     * @throws Exception 购票异常
     */
    ApiResponse<PurchaseRecord> purchaseTicketWithPessimisticLockV2(PurchaseRequest request) throws Exception;
    
    /**
     * 取消购票
     * 1. 验证取消条件
     * 2. 恢复票券库存
     * 3. 更新订单状态
     * 4. 更新相关缓存
     * @param request 取消购票请求
     * @return 取消购票结果
     * @throws Exception 取消购票异常
     */
    CancelPurchaseResponse cancelPurchase(CancelPurchaseRequest request) throws Exception;
    
    /**
     * 查询异步抢购结果
     * @param requestId 请求ID
     * @param userId 用户ID
     * @param date 日期
     * @return 抢购结果
     */
    ApiResponse<Map<String, Object>> getPurchaseResult(String requestId, Long userId, String date);

    ApiResponse<List<TicketOrder>> getOrdersByUserId(Long userId);

    ApiResponse<TicketOrder> getOrderById(Long orderId);
}