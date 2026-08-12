package cn.monitor4all.miaoshaservice.service.impl;

import cn.monitor4all.miaoshaservice.service.CircuitBreakerService;
import cn.monitor4all.miaoshaservice.service.StockRedisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * Redis 库存预扣服务实现
 * 使用 Lua 脚本保证库存扣减/回补的原子性
 */
@Service
public class StockRedisServiceImpl implements StockRedisService {

    private static final Logger LOGGER = LoggerFactory.getLogger(StockRedisServiceImpl.class);

    /**
     * Redis Key 前缀：ticket:stock:{date}
     */
    private static final String STOCK_KEY_PREFIX = "ticket:stock:";

    /**
     * 库存 Key 过期时间（小时）：覆盖一天 + 1小时余量
     */
    private static final long STOCK_KEY_TTL_HOURS = 25L;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CircuitBreakerService circuitBreakerService;

    /**
     * Lua 扣减脚本（预加载，避免每次执行都读取文件）
     */
    private DefaultRedisScript<Long> deductStockScript;

    /**
     * Lua 回补脚本（预加载）
     */
    private DefaultRedisScript<Long> revertStockScript;

    @PostConstruct
    public void init() {
        deductStockScript = new DefaultRedisScript<>();
        deductStockScript.setLocation(new ClassPathResource("lua/deduct_stock.lua"));
        deductStockScript.setResultType(Long.class);

        revertStockScript = new DefaultRedisScript<>();
        revertStockScript.setLocation(new ClassPathResource("lua/revert_stock.lua"));
        revertStockScript.setResultType(Long.class);

        LOGGER.info("StockRedisService 初始化完成，Lua 脚本已预加载");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean deductStock(String date, int count) {
        String key = STOCK_KEY_PREFIX + date;
        return circuitBreakerService.executeWithRedisBreaker(
                () -> {
                    Long result = stringRedisTemplate.execute(
                            deductStockScript,
                            Collections.singletonList(key),
                            String.valueOf(count)
                    );
                    boolean success = result != null && result == 1L;
                    if (success) {
                        LOGGER.info("Redis 库存扣减成功，日期: {}, 扣减数量: {}", date, count);
                    } else {
                        LOGGER.warn("Redis 库存不足，日期: {}, 请求扣减: {}", date, count);
                    }
                    return success;
                },
                () -> {
                    LOGGER.warn("Redis 熔断降级：库存扣减降级返回 false，日期: {}, 数量: {}", date, count);
                    return false;
                }
        );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void revertStock(String date, int count) {
        String key = STOCK_KEY_PREFIX + date;
        try {
            Long result = stringRedisTemplate.execute(
                    revertStockScript,
                    Collections.singletonList(key),
                    String.valueOf(count)
            );
            LOGGER.info("Redis 库存回补成功，日期: {}, 回补数量: {}, 结果: {}", date, count, result);
        } catch (Exception e) {
            LOGGER.error("Redis 库存回补异常，日期: {}, 数量: {}, 错误: {}", date, count, e.getMessage(), e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void syncStockFromDb(String date, int totalRemaining) {
        String key = STOCK_KEY_PREFIX + date;
        try {
            stringRedisTemplate.opsForValue().set(
                    key,
                    String.valueOf(totalRemaining),
                    STOCK_KEY_TTL_HOURS,
                    TimeUnit.HOURS
            );
            LOGGER.info("Redis 库存同步成功，日期: {}, 库存值: {}, TTL: {}小时", date, totalRemaining, STOCK_KEY_TTL_HOURS);
        } catch (Exception e) {
            LOGGER.error("Redis 库存同步异常，日期: {}, 库存值: {}, 错误: {}", date, totalRemaining, e.getMessage(), e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Integer getStock(String date) {
        String key = STOCK_KEY_PREFIX + date;
        return circuitBreakerService.executeWithRedisBreaker(
                () -> {
                    String value = stringRedisTemplate.opsForValue().get(key);
                    if (value == null) {
                        LOGGER.warn("Redis 库存 Key 不存在，日期: {}", date);
                        return null;
                    }
                    return Integer.parseInt(value);
                },
                () -> {
                    LOGGER.warn("Redis 熔断降级：库存查询降级返回 null，日期: {}", date);
                    return null;
                }
        );
    }
}
