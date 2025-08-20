package cn.monitor4all.miaoshaservice.service;

/**
 * 缓存删除消息队列服务接口
 * 用于发送缓存删除消息到消息队列，确保缓存删除的可靠性
 */
public interface CacheDeleteMessageService {
    
    /**
     * 发送缓存删除消息
     *
     * @param cacheKey 缓存键
     * @param reason 删除原因
     * @return 是否发送成功
     */
    boolean sendCacheDeleteMessage(String cacheKey, String reason);
    
    /**
     * 发送缓存删除消息（带延迟）
     *
     * @param cacheKey 缓存键
     * @param reason 删除原因
     * @param delaySeconds 延迟时间（秒）
     * @return 是否发送成功
     */
    boolean sendDelayedCacheDeleteMessage(String cacheKey, String reason, long delaySeconds);
    
    /**
     * 批量发送缓存删除消息
     *
     * @param cacheKeys 缓存键列表
     * @param reason 删除原因
     * @return 成功发送的消息数量
     */
    int sendBatchCacheDeleteMessage(java.util.List<String> cacheKeys, String reason);
    
    /**
     * 检查消息队列连接状态
     *
     * @return 是否连接正常
     */
    boolean isConnected();
}
