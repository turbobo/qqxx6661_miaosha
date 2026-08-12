package cn.monitor4all.miaoshaservice.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Resilience4j 熔断器配置
 * <p>
 * 定义三个 CircuitBreaker 实例，分别针对 Redis、数据库和 MQ 操作，
 * 防止依赖服务故障时请求堆积导致雪崩。
 * </p>
 */
@Configuration
public class ResilienceConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(ResilienceConfig.class);

    /**
     * Redis 熔断器
     * <ul>
     *   <li>failureRateThreshold=50%：错误率达到50%触发熔断</li>
     *   <li>slidingWindowSize=10：滑动窗口大小10次调用</li>
     *   <li>waitDurationInOpenState=30s：熔断打开后等待30秒进入半开状态</li>
     *   <li>minimumNumberOfCalls=5：最少5次调用才计算错误率</li>
     * </ul>
     */
    @Bean
    public CircuitBreaker redisCircuitBreaker() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .slidingWindowSize(10)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .minimumNumberOfCalls(5)
                .build();
        CircuitBreaker circuitBreaker = CircuitBreaker.of("redis", config);
        registerStateTransitionListener(circuitBreaker);
        LOGGER.info("Redis 熔断器已初始化");
        return circuitBreaker;
    }

    /**
     * 数据库熔断器
     * <ul>
     *   <li>slowCallDurationThreshold=2000ms：慢调用阈值2秒</li>
     *   <li>slowCallRateThreshold=60%：慢调用率达到60%触发熔断</li>
     *   <li>waitDurationInOpenState=60s：熔断打开后等待60秒进入半开状态</li>
     *   <li>slidingWindowSize=20：滑动窗口大小20次调用</li>
     * </ul>
     */
    @Bean
    public CircuitBreaker dbCircuitBreaker() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slowCallDurationThreshold(Duration.ofMillis(2000))
                .slowCallRateThreshold(60)
                .waitDurationInOpenState(Duration.ofSeconds(60))
                .slidingWindowSize(20)
                .build();
        CircuitBreaker circuitBreaker = CircuitBreaker.of("db", config);
        registerStateTransitionListener(circuitBreaker);
        LOGGER.info("DB 熔断器已初始化");
        return circuitBreaker;
    }

    /**
     * MQ 熔断器
     * <ul>
     *   <li>failureRateThreshold=30%：错误率达到30%触发熔断</li>
     *   <li>waitDurationInOpenState=10s：熔断打开后等待10秒进入半开状态</li>
     *   <li>slidingWindowSize=10：滑动窗口大小10次调用</li>
     * </ul>
     */
    @Bean
    public CircuitBreaker mqCircuitBreaker() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(30)
                .waitDurationInOpenState(Duration.ofSeconds(10))
                .slidingWindowSize(10)
                .build();
        CircuitBreaker circuitBreaker = CircuitBreaker.of("mq", config);
        registerStateTransitionListener(circuitBreaker);
        LOGGER.info("MQ 熔断器已初始化");
        return circuitBreaker;
    }

    /**
     * 注册熔断器状态变化监听器，状态变化时记录 WARN 日志
     */
    private void registerStateTransitionListener(CircuitBreaker circuitBreaker) {
        circuitBreaker.getEventPublisher()
                .onStateTransition(event -> LOGGER.warn("熔断器 [{}] 状态变化: {} -> {}",
                        circuitBreaker.getName(),
                        event.getStateTransition().getFromState(),
                        event.getStateTransition().getToState()))
                .onCallNotPermitted(event -> LOGGER.warn("熔断器 [{}] 拒绝调用，当前状态: {}",
                        circuitBreaker.getName(),
                        circuitBreaker.getState()));
    }
}
