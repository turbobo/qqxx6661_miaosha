package cn.monitor4all.miaoshaservice.service.impl;

import cn.monitor4all.miaoshaservice.service.CircuitBreakerService;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 熔断降级服务实现
 * <p>
 * 使用 Resilience4j CircuitBreaker 封装三类受保护操作的执行逻辑。
 * 熔断器打开或调用被拒绝时，自动降级执行 fallback 逻辑，并记录 WARN 日志。
 * </p>
 */
@Service
public class CircuitBreakerServiceImpl implements CircuitBreakerService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CircuitBreakerServiceImpl.class);

    /** Redis 熔断器 Bean 名称 */
    private static final String REDIS_BREAKER_NAME = "redis";

    /** DB 熔断器 Bean 名称 */
    private static final String DB_BREAKER_NAME = "db";

    /** MQ 熔断器 Bean 名称 */
    private static final String MQ_BREAKER_NAME = "mq";

    private final CircuitBreaker redisCircuitBreaker;
    private final CircuitBreaker dbCircuitBreaker;
    private final CircuitBreaker mqCircuitBreaker;

    public CircuitBreakerServiceImpl(
            @Qualifier("redisCircuitBreaker") CircuitBreaker redisCircuitBreaker,
            @Qualifier("dbCircuitBreaker") CircuitBreaker dbCircuitBreaker,
            @Qualifier("mqCircuitBreaker") CircuitBreaker mqCircuitBreaker) {
        this.redisCircuitBreaker = redisCircuitBreaker;
        this.dbCircuitBreaker = dbCircuitBreaker;
        this.mqCircuitBreaker = mqCircuitBreaker;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> T executeWithRedisBreaker(Supplier<T> supplier, Supplier<T> fallback) {
        return executeWithBreaker(REDIS_BREAKER_NAME, redisCircuitBreaker, supplier, fallback);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> T executeWithDbBreaker(Supplier<T> supplier, Supplier<T> fallback) {
        return executeWithBreaker(DB_BREAKER_NAME, dbCircuitBreaker, supplier, fallback);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> T executeWithMqBreaker(Supplier<T> supplier, Supplier<T> fallback) {
        return executeWithBreaker(MQ_BREAKER_NAME, mqCircuitBreaker, supplier, fallback);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, String> getCircuitBreakerStatus() {
        Map<String, String> statusMap = new HashMap<>();
        statusMap.put(REDIS_BREAKER_NAME, redisCircuitBreaker.getState().name());
        statusMap.put(DB_BREAKER_NAME, dbCircuitBreaker.getState().name());
        statusMap.put(MQ_BREAKER_NAME, mqCircuitBreaker.getState().name());
        return statusMap;
    }

    /**
     * 通用熔断执行模板方法
     * <p>
     * 使用 {@code CircuitBreaker.decorateSupplier()} 将业务逻辑包装在熔断器中执行。
     * 当熔断器打开（{@link CallNotPermittedException}）或业务逻辑抛出异常时，
     * 降级执行 fallback 逻辑。fallback 内部的异常会被捕获并返回 null。
     * </p>
     *
     * @param breakerName    熔断器名称（用于日志标识）
     * @param circuitBreaker 熔断器实例
     * @param supplier       正常业务逻辑
     * @param fallback       降级逻辑
     * @param <T>            返回值类型
     * @return 正常结果或降级结果
     */
    private <T> T executeWithBreaker(String breakerName,
                                     CircuitBreaker circuitBreaker,
                                     Supplier<T> supplier,
                                     Supplier<T> fallback) {
        Supplier<T> decoratedSupplier = CircuitBreaker.decorateSupplier(circuitBreaker, supplier);
        try {
            return decoratedSupplier.get();
        } catch (CallNotPermittedException e) {
            // 熔断器打开，调用被拒绝
            LOGGER.warn("熔断器 [{}] 已打开，执行降级逻辑", breakerName);
            return safeFallback(breakerName, fallback);
        } catch (Exception e) {
            // 业务逻辑异常（熔断器会记录失败次数）
            LOGGER.warn("熔断器 [{}] 内业务异常，执行降级逻辑: {}", breakerName, e.getMessage());
            return safeFallback(breakerName, fallback);
        }
    }

    /**
     * 安全执行降级逻辑
     * <p>
     * 保证 fallback 不会抛出异常。若 fallback 本身异常，记录错误日志并返回 null。
     * </p>
     */
    private <T> T safeFallback(String breakerName, Supplier<T> fallback) {
        try {
            return fallback.get();
        } catch (Exception fallbackEx) {
            LOGGER.error("熔断器 [{}] 降级逻辑执行异常: {}", breakerName, fallbackEx.getMessage(), fallbackEx);
            return null;
        }
    }
}
