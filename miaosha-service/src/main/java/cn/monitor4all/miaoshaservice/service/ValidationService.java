package cn.monitor4all.miaoshaservice.service;

import java.util.Map;

/**
 * 验证服务接口
 * 负责处理票券和用户的验证逻辑，避免循环依赖
 */
public interface ValidationService {
    
    /**
     * 验证票数合法性
     * @param date 票券日期
     * @return 验证结果
     */
    Map<String, Object> validateTicketCount(String date);
    
    /**
     * 验证票数合法性（抛出异常版本）
     * @param date 票券日期
     */
    void validateTicketCountWithException(String date);
    
    /**
     * 验证用户是否存在
     * @param userId 用户ID
     * @return 验证结果
     */
    Map<String, Object> validateUser(Long userId);
    
    /**
     * 验证用户是否存在（抛出异常版本）
     * @param userId 用户ID
     */
    void validateUserWithException(Long userId);
    
    /**
     * 验证抢购时间是否有效
     * @param date 票券日期
     * @return 验证结果
     */
    Map<String, Object> validatePurchaseTime(String date);
    
    /**
     * 验证抢购时间是否有效（抛出异常版本）
     * @param date 票券日期
     */
    void validatePurchaseTimeWithException(String date);
    
    /**
     * 综合验证购买请求
     * @param userId 用户ID
     * @param date 票券日期
     * @return 验证结果
     */
    Map<String, Object> validatePurchaseRequest(Long userId, String date);
}