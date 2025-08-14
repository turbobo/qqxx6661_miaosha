package cn.monitor4all.miaoshaservice.service.impl;

import cn.monitor4all.miaoshadao.dao.Stock;
import cn.monitor4all.miaoshadao.dao.User;
import cn.monitor4all.miaoshadao.mapper.UserMapper;
import cn.monitor4all.miaoshadao.utils.CacheKey;
import cn.monitor4all.miaoshaservice.service.StockService;
import cn.monitor4all.miaoshaservice.service.ValidationService;
import cn.monitor4all.miaoshaservice.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import org.springframework.util.DigestUtils;

import java.time.LocalTime;
import java.util.concurrent.TimeUnit;

@Service
public class UserServiceImpl implements UserService {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserServiceImpl.class);

    private static final String SALT = "randomString";
    private static final int ALLOW_COUNT = 10;

    // 设置抢购开始时间
    private static final LocalTime START_TIME = LocalTime.of(9, 0); // 上午9点开始
    // 设置抢购结束时间
    private static final LocalTime END_TIME = LocalTime.of(23, 0);  // 晚上11点结束

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private UserMapper userMapper;

    @Resource
    private StockService stockService;


    @Resource
    private ValidationService validationService;

    @Override
    public String getVerifyHash(Integer sid, Long userId) throws Exception {

        // 验证是否在抢购时间内
        LocalTime now = LocalTime.now();
        if (now.isBefore(START_TIME) || now.isAfter(END_TIME)) {
            LOGGER.warn("当前时间不在抢购时间内，当前时间：{}，抢购时间：{}-{}", now, START_TIME, END_TIME);
            throw new Exception("不在抢购时间内，抢购时间为每天" + START_TIME + "到" + END_TIME);
        }
        LOGGER.info("抢购时间验证通过，当前时间：{}", now);


        // 检查用户合法性
        User user = userMapper.selectByPrimaryKey(userId);
        if (user == null) {
            throw new Exception("用户不存在");
        }
        LOGGER.info("用户信息：[{}]", user.toString());

        // 检查商品合法性
        Stock stock = stockService.getStockById(sid);
        if (stock == null) {
            throw new Exception("商品不存在");
        }
        LOGGER.info("商品信息：[{}]", stock.toString());

        // 生成hash
        String verify = SALT + sid + userId;
        String verifyHash = DigestUtils.md5DigestAsHex(verify.getBytes());

        // 将hash和用户商品信息存入redis
        String hashKey = CacheKey.HASH_KEY.getKey() + "_" + sid + "_" + userId;
        stringRedisTemplate.opsForValue().set(hashKey, verifyHash, 3600, TimeUnit.SECONDS);
        LOGGER.info("Redis写入：[{}] [{}]", hashKey, verifyHash);
        return verifyHash;
    }

    @Override
    public String getVerifyHash4Ticket(String date, Long userId) throws Exception {
        // 检查用户合法性
        validUser(userId);

        // 检查票数合法性
        validationService.validateTicketCountWithException(date);

        // 生成hash
        String verify = SALT + date + userId;
        String verifyHash = DigestUtils.md5DigestAsHex(verify.getBytes());

        // 将hash和用户商品信息存入redis
        String hashKey = CacheKey.HASH_KEY.getKey() + "_" + date + "_" + userId;
        stringRedisTemplate.opsForValue().set(hashKey, verifyHash, 3600, TimeUnit.SECONDS);
        LOGGER.info("Redis写入：[{}] [{}]", hashKey, verifyHash);
        return verifyHash;
    }

    @Override
    public int addUserCount(Long userId) throws Exception {
        String limitKey = CacheKey.LIMIT_KEY.getKey() + "_" + userId;
        stringRedisTemplate.opsForValue().setIfAbsent(limitKey, "0", 60, TimeUnit.SECONDS);
        Long limit = stringRedisTemplate.opsForValue().increment(limitKey);
        return Integer.parseInt(String.valueOf(limit));
    }

    @Override
    public boolean getUserIsBanned(Long userId) {
        String limitKey = CacheKey.LIMIT_KEY.getKey() + "_" + userId;
        String limit = stringRedisTemplate.opsForValue().get(limitKey);
        if (limit == null) {
            return false;
        }
        return Integer.parseInt(limit) > ALLOW_COUNT;
    }

    public void validUser(Long userId) throws Exception {
        User user = userMapper.selectByPrimaryKey(userId);
        if (user == null) {
            throw new Exception("用户不存在");
        }
        LOGGER.info("用户信息：[{}]", user.toString());
    }
}