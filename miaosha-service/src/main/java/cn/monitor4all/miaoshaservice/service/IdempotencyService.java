package cn.monitor4all.miaoshaservice.service;

/**
 * 幂等性保障服务
 * <p>
 * 提供三重幂等检查机制，防止同一请求被重复处理：
 * <ul>
 *     <li>基于 requestId 的 Redis SETNX 原子标记</li>
 *     <li>基于 user_id + date 的 Redis 购买状态检查</li>
 *     <li>数据库唯一索引兜底</li>
 * </ul>
 */
public interface IdempotencyService {

    /**
     * 检查请求是否已处理，未处理则原子标记。
     * <p>
     * 使用 Redis SETNX 实现原子性检查与标记，key 有效期 5 分钟。
     * 如果 requestId 为空或 null，跳过幂等检查直接返回 true（向后兼容旧客户端）。
     *
     * @param requestId 幂等请求ID（前端生成的UUID）
     * @return true 表示首次请求（设置成功），false 表示重复请求（key 已存在）
     */
    boolean checkAndMark(String requestId);

    /**
     * 检查用户是否已购买该日期票券。
     * <p>
     * 通过 Redis key {@code USER_HAS_ORDER_{date}_{userId}} 判断。
     *
     * @param userId 用户ID
     * @param date   购票日期字符串
     * @return true 表示已购买，false 表示未购买
     */
    boolean hasUserPurchased(Long userId, String date);

    /**
     * 处理失败时移除幂等标记，允许客户端重试。
     * <p>
     * 仅在系统异常等非业务预期失败时调用，业务失败（如库存不足）不应移除。
     *
     * @param requestId 幂等请求ID
     */
    void removeIdempotentMark(String requestId);
}
