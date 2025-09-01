package cn.monitor4all.miaoshaservice.service;

import cn.monitor4all.miaoshadao.model.PurchaseRecord;
import cn.monitor4all.miaoshadao.model.Ticket;

import java.util.List;

/**
 * 票券缓存管理器接口
 * 提供票券缓存的基本操作方法
 */
public interface TicketCacheManager {
    
    /**
     * 获取指定日期的票券信息
     * @param date 票券日期
     * @return 票券信息
     */
    Ticket getTicketWithFallback(String date);
    
    /**
     * 保存票券信息到缓存
     * @param date 票券日期
     * @param ticket 票券信息
     */
    void saveTicket(String date, Ticket ticket);
    
    /**
     * 删除指定日期的票券缓存
     * @param date 票券日期
     */
    void deleteTicket(String date);
    
    /**
     * 获取票券列表
     * @return 票券列表
     */
    List<Ticket> getTicketList();
    
    /**
     * 保存票券列表到缓存
     * @param tickets 票券列表
     */
    void saveTicketList(List<Ticket> tickets);
    
    /**
     * 添加购买记录到缓存
     * @param userId 用户ID
     * @param date 购买日期
     * @param record 购买记录
     */
    void addPurchaseRecord(Long userId, String date, PurchaseRecord record);
    
    /**
     * 获取用户的购买记录（仅从缓存）
     * @param userId 用户ID
     * @param date 购买日期
     * @return 购买记录，如果缓存中没有则返回null
     */
    PurchaseRecord getPurchaseRecord(Long userId, String date);
    
    /**
     * 获取用户的所有购买记录（仅从缓存）
     * @param userId 用户ID
     * @return 购买记录列表，如果缓存中没有则返回null
     */
    List<PurchaseRecord> getPurchaseRecords(Long userId);
    
    /**
     * 获取用户的购买记录（缓存优先，数据库兜底）
     * @param userId 用户ID
     * @param date 购买日期
     * @return 购买记录，如果都没有则返回null
     */
    PurchaseRecord getPurchaseRecordWithFallback(Long userId, String date);
    
    /**
     * 获取用户的所有购买记录（缓存优先，数据库兜底）
     * @param userId 用户ID
     * @return 购买记录列表，如果都没有则返回空列表
     */
    List<PurchaseRecord> getPurchaseRecordsWithFallback(Long userId);
    
    /**
     * 清空所有票券缓存
     */
    void clearAllTicketCache();
    
    /**
     * 检查Redis连接状态
     * @return 是否连接正常
     */
    boolean isRedisConnected();
    
    /**
     * 删除购买记录缓存
     * @param userId 用户ID
     * @param date 购买日期
     */
    void deletePurchaseRecord(Long userId, String date);
    
    /**
     * 删除票券列表缓存
     */
    void deleteTicketList();

    // 清除购票状态缓存
    void clearUserPurchaseStatus(Long userId, String date);
}
