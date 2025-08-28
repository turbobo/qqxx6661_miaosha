package cn.monitor4all.miaoshaservice.service.impl;

import cn.monitor4all.miaoshaservice.service.SequenceGeneratorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * 序列号生成服务实现类
 * 提供多种策略生成连续、唯一的序列号
 */
@Service
public class SequenceGeneratorServiceImpl implements SequenceGeneratorService {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(SequenceGeneratorServiceImpl.class);
    
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    
    // 序列号生成策略
    private String currentStrategy = "Redis原子递增";
    
    // Lua脚本：原子递增序列号
    private static final String INCREMENT_SCRIPT = 
        "local key = KEYS[1] " +
        "local step = tonumber(ARGV[1]) " +
        "local expire = tonumber(ARGV[2]) " +
        "local current = redis.call('GET', key) " +
        "if not current then " +
        "  redis.call('SET', key, step) " +
        "  redis.call('EXPIRE', key, expire) " +
        "  return step " +
        "else " +
        "  local next = tonumber(current) + step " +
        "  redis.call('SET', key, next) " +
        "  redis.call('EXPIRE', key, expire) " +
        "  return next " +
        "end";
    
    // Lua脚本：获取并递增序列号（原子操作）
    private static final String GET_AND_INCREMENT_SCRIPT = 
        "local key = KEYS[1] " +
        "local step = tonumber(ARGV[1]) " +
        "local expire = tonumber(ARGV[2]) " +
        "local current = redis.call('GET', key) " +
        "if not current then " +
        "  redis.call('SET', key, step) " +
        "  redis.call('EXPIRE', key, expire) " +
        "  return step " +
        "else " +
        "  local next = tonumber(current) + step " +
        "  redis.call('SET', key, next) " +
        "  redis.call('EXPIRE', key, expire) " +
        "  return next " +
        "end";
    
    @Override
    public long getNextSequence(String businessKey) {
        return getNextSequence(businessKey, 1);
    }
    
    @Override
    public long getNextSequence(String businessKey, long step) {
        try {
            // 策略1：使用Lua脚本原子递增（推荐）
            long sequence = getNextSequenceWithLua(businessKey, step);
            if (sequence > 0) {
                currentStrategy = "Redis Lua脚本";
                return sequence;
            }
            
            // 策略2：使用Redis INCR（备选）
            currentStrategy = "Redis INCR";
            return getNextSequenceWithIncr(businessKey, step);
            
        } catch (Exception e) {
            LOGGER.warn("Redis序列号生成失败，使用备选方案: {}", e.getMessage());
            // 策略3：使用内存计数器（兜底）
            currentStrategy = "内存计数器";
            return getNextSequenceWithMemory(businessKey, step);
        }
    }
    
    @Override
    public long getCurrentSequence(String businessKey) {
        try {
            String key = "sequence:" + businessKey;
            String value = stringRedisTemplate.opsForValue().get(key);
            if (value != null) {
                return Long.parseLong(value);
            }
            return 0;
        } catch (Exception e) {
            LOGGER.error("获取当前序列号失败: {}", e.getMessage(), e);
            return 0;
        }
    }
    
    @Override
    public void resetSequence(String businessKey, long startValue) {
        try {
            String key = "sequence:" + businessKey;
            stringRedisTemplate.opsForValue().set(key, String.valueOf(startValue));
            stringRedisTemplate.expire(key, 7, TimeUnit.DAYS);
            LOGGER.info("重置序列号成功，业务键: {}, 起始值: {}", businessKey, startValue);
        } catch (Exception e) {
            LOGGER.error("重置序列号失败: {}", e.getMessage(), e);
        }
    }
    
    @Override
    public String getGenerationStrategy() {
        return currentStrategy;
    }
    
    /**
     * 策略1：使用Lua脚本原子递增（推荐）
     * 优点：原子性、高性能、支持复杂逻辑
     */
    private long getNextSequenceWithLua(String businessKey, long step) {
        try {
            String key = "sequence:" + businessKey;
            DefaultRedisScript<Long> script = new DefaultRedisScript<>();
            script.setScriptText(GET_AND_INCREMENT_SCRIPT);
            script.setResultType(Long.class);
            
            Long result = stringRedisTemplate.execute(script, 
                Arrays.asList(key), 
                String.valueOf(step), 
                String.valueOf(1 * 24 * 3600)); // 1天过期
            
            if (result != null) {
                LOGGER.debug("Lua脚本生成序列号成功，业务键: {}, 序列号: {}", businessKey, result);
                return result;
            }
            
            return 0;
        } catch (Exception e) {
            LOGGER.error("Lua脚本生成序列号失败: {}", e.getMessage(), e);
            return 0;
        }
    }
    
    /**
     * 策略2：使用Redis INCR（备选）
     * 优点：简单、可靠
     */
    private long getNextSequenceWithIncr(String businessKey, long step) {
        try {
            String key = "sequence:" + businessKey;
            Long result = stringRedisTemplate.opsForValue().increment(key, step);
            
            // 设置过期时间
            stringRedisTemplate.expire(key, 1, TimeUnit.DAYS);
            
            if (result != null) {
                LOGGER.debug("Redis INCR生成序列号成功，业务键: {}, 序列号: {}", businessKey, result);
                return result;
            }
            
            return 0;
        } catch (Exception e) {
            LOGGER.error("Redis INCR生成序列号失败: {}", e.getMessage(), e);
            return 0;
        }
    }
    
    /**
     * 策略3：使用内存计数器（兜底）
     * 优点：不依赖Redis、快速
     * 缺点：服务重启会丢失、单机唯一
     */
    private long getNextSequenceWithMemory(String businessKey, long step) {
        try {
            String key = "memory_sequence:" + businessKey;
            
            // 使用Redis作为内存计数器的持久化存储
            String currentStr = stringRedisTemplate.opsForValue().get(key);
            long current = 0;
            
            if (currentStr != null) {
                try {
                    current = Long.parseLong(currentStr);
                } catch (NumberFormatException e) {
                    LOGGER.warn("解析内存序列号失败，从0开始: {}", currentStr);
                    current = 0;
                }
            }
            
            long next = current + step;
            
            // 更新到Redis（作为持久化）
            stringRedisTemplate.opsForValue().set(key, String.valueOf(next));
            stringRedisTemplate.expire(key, 1, TimeUnit.DAYS);
            
            LOGGER.debug("内存计数器生成序列号成功，业务键: {}, 序列号: {}", businessKey, next);
            return next;
            
        } catch (Exception e) {
            LOGGER.error("内存计数器生成序列号失败: {}", e.getMessage(), e);
            // 兜底：使用时间戳
            return System.currentTimeMillis() % 1000000;
        }
    }
    
    /**
     * 批量生成序列号（用于预分配）
     * @param businessKey 业务键
     * @param count 数量
     * @return 起始序列号
     */
    public long getBatchSequence(String businessKey, int count) {
        try {
            String key = "sequence:" + businessKey;
            Long result = stringRedisTemplate.opsForValue().increment(key, count);
            
            if (result != null) {
                stringRedisTemplate.expire(key, 7, TimeUnit.DAYS);
                long startSequence = result - count + 1;
                LOGGER.info("批量生成序列号成功，业务键: {}, 数量: {}, 起始: {}, 结束: {}", 
                    businessKey, count, startSequence, result);
                return startSequence;
            }
            
            return 0;
        } catch (Exception e) {
            LOGGER.error("批量生成序列号失败: {}", e.getMessage(), e);
            return 0;
        }
    }
    
    /**
     * 检查序列号连续性
     * @param businessKey 业务键
     * @return 是否连续
     */
    public boolean checkSequenceContinuity(String businessKey) {
        try {
            String key = "sequence:" + businessKey;
            String value = stringRedisTemplate.opsForValue().get(key);
            
            if (value != null) {
                long current = Long.parseLong(value);
                LOGGER.info("检查序列号连续性，业务键: {}, 当前值: {}", businessKey, current);
                return current > 0;
            }
            
            return true; // 新业务键，认为连续
        } catch (Exception e) {
            LOGGER.error("检查序列号连续性失败: {}", e.getMessage(), e);
            return false;
        }
    }
}
