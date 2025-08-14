package cn.monitor4all.miaoshaservice.service.impl;

import cn.monitor4all.miaoshadao.dao.Stock;
import cn.monitor4all.miaoshadao.mapper.StockMapper;
import cn.monitor4all.miaoshadao.utils.CacheKey;
import cn.monitor4all.miaoshaservice.service.StockService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class StockServiceImpl implements StockService {
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    static {

        UNLOCK_SCRIPT = new DefaultRedisScript();

        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));

        UNLOCK_SCRIPT.setResultType(Long.class);

    }

    private static final Logger LOGGER = LoggerFactory.getLogger(StockServiceImpl.class);

    @Resource
    private StockMapper stockMapper;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Integer getStockCount(int sid) {
        Integer stockLeft;
        stockLeft = getStockCountByCache(sid);
        LOGGER.info("缓存中取得库存数：[{}]", stockLeft);
        if (stockLeft == null) {
            stockLeft = getStockCountByDB(sid);
            LOGGER.info("缓存未命中，查询数据库，并写入缓存");
            setStockCountCache(sid, stockLeft);
        }
        return stockLeft;
    }

    @Override
    public int getStockCountByDB(int id) {
        Stock stock = stockMapper.selectByPrimaryKey(id);
        return stock.getCount() - stock.getSale();
    }

    @Override
    public Integer getStockCountByCache(int id) {
        String hashKey = CacheKey.STOCK_COUNT.getKey() + "_" + id;
        String countStr = stringRedisTemplate.opsForValue().get(hashKey);
        if (countStr != null) {
            return Integer.parseInt(countStr);
        } else {
            return null;
        }
    }

    @Override
    public void setStockCountCache(int id, int count) {
        String hashKey = CacheKey.STOCK_COUNT.getKey() + "_" + id;
        String countStr = String.valueOf(count);
        LOGGER.info("写入商品库存缓存: [{}] [{}]", hashKey, countStr);
        stringRedisTemplate.opsForValue().set(hashKey, countStr, 3600, TimeUnit.SECONDS);
    }

    @Override
    public void delStockCountCache(int id) {
        String hashKey = CacheKey.STOCK_COUNT.getKey() + "_" + id;
        stringRedisTemplate.delete(hashKey);
        LOGGER.info("删除商品id：[{}] 缓存", id);
    }

    @Override
    public Stock getStockById(int id) {
        return stockMapper.selectByPrimaryKey(id);
    }

    @Override
    public Stock getStockByIdForUpdate(int id) {
        return stockMapper.selectByPrimaryKeyForUpdate(id);
    }

    @Override
    public int updateStockById(Stock stock) {
        return stockMapper.updateByPrimaryKeySelective(stock);
    }

    @Override
    public int updateStockByOptimistic(Stock stock) {
        return stockMapper.updateByOptimistic(stock);
    }

    //分布式锁
    public void tryLock() {
        Boolean lock = stringRedisTemplate.opsForValue().setIfAbsent("lockKey", String.valueOf(Thread.currentThread().getId()), 3, TimeUnit.SECONDS);
    }

    public void unLock() {
        stringRedisTemplate.execute(UNLOCK_SCRIPT

                , Collections.singletonList("KEY_PREFIX + name")

                ,"VALUE_PREFIX" + Thread.currentThread().getId());
    }

    public boolean acquireLock(String lockKey, String clientId, long expireTimeMillis) {
        String script = "if redis.call('setnx', KEYS[1], ARGV[1]) == 1 then " +
                "redis.call('pexpire', KEYS[1], ARGV[2]) return true " +
                "else return false end";
        RedisScript<Boolean> redisScript = new DefaultRedisScript<>(script, Boolean.class);
        List<String> keys = Collections.singletonList(lockKey);
        Boolean result = stringRedisTemplate.execute(redisScript, keys, clientId, String.valueOf(expireTimeMillis));
        return result != null && result;
    }

    /**
     * 在 Lua 脚本中，return 1 会转换为布尔值 true。在 Redis 的 Lua 脚本中，非空的返回值都会被视为真（true），而空值（nil）或者返回 0 都会被视为假（false）。
     * 因此，当 Lua 脚本中的 return 返回非空值时，它会被转换为布尔值 true。
     * @param lockKey
     * @param clientId 可以用当前现场的id String.valueOf(Thread.currentThread().getId())
     * @return
     */
    public boolean releaseLock(String lockKey, String clientId) {
        String script = "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                "return redis.call('del', KEYS[1]) else return 0 end";
        RedisScript<Long> redisScript = new DefaultRedisScript<>(script, Long.class);
        List<String> keys = Collections.singletonList(lockKey);
        Long result = stringRedisTemplate.execute(redisScript, keys, clientId);
        return result != null && result == 1;
    }

    @Override
    public boolean adminUpdateStock(int sid, int count) {
        // 获取分布式锁
        String lockKey = "ADMIN_UPDATE_STOCK_" + sid;
        String clientId = String.valueOf(Thread.currentThread().getId());
        
        try {
            // 尝试获取分布式锁，等待3秒
            boolean locked = acquireLock(lockKey, clientId, 3000);
            if (!locked) {
                LOGGER.error("获取分布式锁失败，商品id: [{}]", sid);
                return false;
            }
            
            // 查询当前库存信息
            Stock stock = stockMapper.selectByPrimaryKey(sid);
            if (stock == null) {
                LOGGER.error("商品不存在，id: [{}]", sid);
                return false;
            }
            
            // 计算新的sale值：保持已售出数量不变，调整总库存
            int newVersion = stock.getVersion() + 1;
            stock.setCount(count);  // 设置新的总库存
            stock.setVersion(newVersion);  // 更新版本号
            
            // 更新数据库
            int result = stockMapper.updateByPrimaryKeySelective(stock);
            if (result == 0) {
                LOGGER.error("更新库存失败，商品id: [{}]", sid);
                return false;
            }
            
            // 删除缓存
            delStockCountCache(sid);
            
            LOGGER.info("管理员更新库存成功，商品id: [{}], 新库存: [{}]", sid, count);
            return true;
            
        } catch (Exception e) {
            LOGGER.error("管理员更新库存异常，商品id: [{}], 异常: [{}]", sid, e.getMessage());
            return false;
        } finally {
            // 释放分布式锁
            releaseLock(lockKey, clientId);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public boolean adminUpdateStockWithPessimistic(int sid, int count) {
        try {
            // 使用悲观锁查询当前库存信息
            Stock stock = stockMapper.selectByPrimaryKeyForUpdate(sid);
            if (stock == null) {
                LOGGER.error("商品不存在，id: [{}]", sid);
                return false;
            }
            
            // 验证新库存数量的合理性
            if (count < stock.getSale()) {
                LOGGER.error("新库存数量不能小于已售出数量，商品id: [{}], 已售出: [{}], 新库存: [{}]", 
                    sid, stock.getSale(), count);
                return false;
            }
            
            // 更新库存信息
            stock.setCount(count);
            stock.setVersion(stock.getVersion() + 1);
            
            // 使用悲观锁更新
            int result = stockMapper.updateByAdminWithPessimistic(stock);
            if (result == 0) {
                LOGGER.error("悲观锁更新库存失败，商品id: [{}]", sid);
                return false;
            }
            
            // 删除缓存
            delStockCountCache(sid);
            
            LOGGER.info("管理员悲观锁更新库存成功，商品id: [{}], 原库存: [{}], 新库存: [{}], 已售出: [{}]", 
                sid, stock.getCount(), count, stock.getSale());
            return true;
            
        } catch (Exception e) {
            LOGGER.error("管理员悲观锁更新库存异常，商品id: [{}], 异常: [{}]", sid, e.getMessage());
            throw new RuntimeException("更新库存失败", e);
        }
    }
}
