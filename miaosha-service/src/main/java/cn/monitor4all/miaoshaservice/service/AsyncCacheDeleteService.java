package cn.monitor4all.miaoshaservice.service;

/**
 * 异步缓存删除服务接口
 * 支持线程池异步删除和队列异步删除两种方式
 */
public interface AsyncCacheDeleteService {
    
    /**
     * 异步删除缓存（线程池方式）
     * @param cacheKey 缓存键
     */
    void deleteCacheAsync(String cacheKey);
    
    /**
     * 异步删除缓存（线程池方式）
     * @param cacheKey 缓存键
     * @param delayMillis 延迟删除时间（毫秒）
     */
    void deleteCacheAsync(String cacheKey, long delayMillis);
    
    /**
     * 异步删除缓存（队列方式）
     * @param cacheKey 缓存键
     */
    void deleteCacheByQueue(String cacheKey);
    
    /**
     * 异步删除缓存（队列方式）
     * @param cacheKey 缓存键
     * @param delayMillis 延迟删除时间（毫秒）
     */
    void deleteCacheByQueue(String cacheKey, long delayMillis);
    
    /**
     * 双重异步删除缓存（先线程池，再队列）
     * @param cacheKey 缓存键
     */
    void deleteCacheDualAsync(String cacheKey);
    
    /**
     * 双重异步删除缓存（先线程池，再队列）
     * @param cacheKey 缓存键
     * @param delayMillis 延迟删除时间（毫秒）
     */
    void deleteCacheDualAsync(String cacheKey, long delayMillis);
    
    /**
     * 批量异步删除缓存
     * @param cacheKeys 缓存键列表
     */
    void deleteCacheBatchAsync(java.util.List<String> cacheKeys);
}
