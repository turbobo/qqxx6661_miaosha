package cn.monitor4all.miaoshaservice.service;

import cn.monitor4all.miaoshadao.dao.TicketEntity;
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
     * 购买票券
     * @param userId 用户ID
     * @param date 日期
     * @return 购买记录，如果购买失败返回null
     */
    ApiResponse<PurchaseRecord> purchaseTicket(PurchaseRequest request) throws Exception;


    PurchaseRecord purchaseTicketV2(PurchaseRequest request) throws Exception;

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
}