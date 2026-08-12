package cn.monitor4all.miaoshaservice.service;

import java.util.Map;
import java.util.function.Supplier;

/**
 * 熔断降级服务接口
 * <p>
 * 封装 Resilience4j 熔断器调用，提供 Redis、DB、MQ 三类受保护的执行方法。
 * 当熔断器打开时自动调用降级逻辑，防止故障扩散。
 * </p>
 */
public interface CircuitBreakerService {

    /**
     * 执行受 Redis 熔断保护的操作
     *
     * @param supplier 正常业务逻辑
     * @param fallback 降级逻辑（熔断打开或异常时调用），不得抛出异常
     * @param <T>      返回值类型
     * @return 正常结果或降级结果
     */
    <T> T executeWithRedisBreaker(Supplier<T> supplier, Supplier<T> fallback);

    /**
     * 执行受数据库熔断保护的操作
     *
     * @param supplier 正常业务逻辑
     * @param fallback 降级逻辑（熔断打开或异常时调用），不得抛出异常
     * @param <T>      返回值类型
     * @return 正常结果或降级结果
     */
    <T> T executeWithDbBreaker(Supplier<T> supplier, Supplier<T> fallback);

    /**
     * 执行受 MQ 熔断保护的操作
     *
     * @param supplier 正常业务逻辑
     * @param fallback 降级逻辑（熔断打开或异常时调用），不得抛出异常
     * @param <T>      返回值类型
     * @return 正常结果或降级结果
     */
    <T> T executeWithMqBreaker(Supplier<T> supplier, Supplier<T> fallback);

    /**
     * 获取所有熔断器的当前状态（用于监控接口）
     *
     * @return 熔断器名称 → 状态字符串（CLOSED / OPEN / HALF_OPEN / DISABLED / FORCED_OPEN）
     */
    Map<String, String> getCircuitBreakerStatus();
}
