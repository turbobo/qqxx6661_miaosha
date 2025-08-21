package cn.monitor4all.miaoshaservice.service;

/**
 * 票券编码生成服务接口
 * 提供多种策略生成唯一票券编码，确保编码的唯一性
 */
public interface TicketCodeGeneratorService {
    
    /**
     * 生成唯一票券编码
     * @param userId 用户ID
     * @param date 日期
     * @return 唯一票券编码
     */
    String generateUniqueTicketCode(String userId, String date);
    
    /**
     * 生成唯一票券编码（带重试机制）
     * @param userId 用户ID
     * @param date 日期
     * @param maxRetries 最大重试次数
     * @return 唯一票券编码
     */
    String generateUniqueTicketCode(String userId, String date, int maxRetries);
    
    /**
     * 验证票券编码唯一性
     * @param ticketCode 票券编码
     * @return 是否唯一
     */
    boolean isTicketCodeUnique(String ticketCode);
    
    /**
     * 获取票券编码生成策略信息
     * @return 策略信息
     */
    String getGenerationStrategy();
}
