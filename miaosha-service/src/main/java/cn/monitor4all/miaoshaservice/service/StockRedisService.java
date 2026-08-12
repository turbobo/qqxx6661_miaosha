package cn.monitor4all.miaoshaservice.service;

/**
 * Redis 库存预扣服务
 * 使用 Lua 脚本实现原子库存扣减，替代 MySQL FOR UPDATE 悲观锁
 */
public interface StockRedisService {

    /**
     * Lua 原子扣减库存
     *
     * @param date  票券日期（格式：yyyy-MM-dd）
     * @param count 扣减数量（通常为1）
     * @return true 表示扣减成功，false 表示库存不足
     */
    boolean deductStock(String date, int count);

    /**
     * 回补库存（订单创建失败时调用）
     *
     * @param date  票券日期（格式：yyyy-MM-dd）
     * @param count 回补数量（通常为1）
     */
    void revertStock(String date, int count);

    /**
     * 从 DB 同步库存到 Redis（定时任务或管理员操作时调用）
     *
     * @param date           票券日期（格式：yyyy-MM-dd）
     * @param totalRemaining DB 中实际剩余库存数
     */
    void syncStockFromDb(String date, int totalRemaining);

    /**
     * 查询 Redis 中的库存值
     *
     * @param date 票券日期（格式：yyyy-MM-dd）
     * @return 库存数量，若 key 不存在返回 null
     */
    Integer getStock(String date);
}
