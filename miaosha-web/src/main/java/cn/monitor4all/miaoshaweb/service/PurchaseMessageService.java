package cn.monitor4all.miaoshaweb.service;

import cn.monitor4all.miaoshaweb.model.PurchaseMessage;
import cn.monitor4all.miaoshaweb.model.PurchaseResult;

/**
 * 抢购消息服务接口
 * 负责处理抢购消息的发送、状态管理和结果查询
 */
public interface PurchaseMessageService {
    
    /**
     * 发送抢购消息到消息队列
     *
     * @param purchaseMessage 抢购消息
     * @return 是否发送成功
     */
    boolean sendPurchaseMessage(PurchaseMessage purchaseMessage);
    
    /**
     * 查询抢购结果
     *
     * @param messageId 消息ID
     * @return 抢购结果
     */
    PurchaseResult getPurchaseResult(String messageId);
    
    /**
     * 查询用户指定日期的抢购结果
     *
     * @param userId 用户ID
     * @param date 票券日期
     * @return 抢购结果
     */
    PurchaseResult getPurchaseResultByUserAndDate(Long userId, String date);
    
    /**
     * 检查用户是否已有指定日期的订单
     *
     * @param userId 用户ID
     * @param date 票券日期
     * @return 是否已有订单
     */
    boolean hasExistingOrder(Long userId, String date);
    
    /**
     * 更新抢购消息状态
     *
     * @param messageId 消息ID
     * @param status 新状态
     * @param result 处理结果
     * @param errorMessage 错误信息
     */
    void updateMessageStatus(String messageId, String status, String result, String errorMessage);
    
    /**
     * 处理抢购消息
     * 基于purchaseTicketV1方法的逻辑进行抢购
     *
     * @param purchaseMessage 抢购消息
     */
    void processPurchaseMessage(PurchaseMessage purchaseMessage);
    
    /**
     * 重试处理失败的消息
     *
     * @param messageId 消息ID
     * @return 是否重试成功
     */
    boolean retryFailedMessage(String messageId);
    
    /**
     * 清理过期的消息记录
     *
     * @param expireHours 过期时间（小时）
     * @return 清理的消息数量
     */
    int cleanupExpiredMessages(int expireHours);
}
