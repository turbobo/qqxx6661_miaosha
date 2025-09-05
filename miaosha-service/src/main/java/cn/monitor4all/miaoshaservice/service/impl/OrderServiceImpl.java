package cn.monitor4all.miaoshaservice.service.impl;

import cn.monitor4all.miaoshadao.dao.OrderRecord;
import cn.monitor4all.miaoshadao.dao.Stock;
import cn.monitor4all.miaoshadao.dao.StockOrder;
import cn.monitor4all.miaoshadao.dao.User;
import cn.monitor4all.miaoshadao.mapper.StockOrderMapper;
import cn.monitor4all.miaoshadao.mapper.UserMapper;
import cn.monitor4all.miaoshadao.utils.CacheKey;
import cn.monitor4all.miaoshaservice.constant.OrderRecordStatus;
import cn.monitor4all.miaoshaservice.service.OrderService;
import cn.monitor4all.miaoshaservice.service.StockService;
import com.alibaba.fastjson.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class OrderServiceImpl implements OrderService {

    private static final Logger LOGGER = LoggerFactory.getLogger(OrderServiceImpl.class);

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private StockService stockService;

    @Resource
    private OrderService orderService;

    @Resource
    private StockOrderMapper orderMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private AmqpTemplate rabbitTemplate;

    @Override
    public int createWrongOrder(int sid) {
        //校验库存
        Stock stock = checkStock(sid);
        //扣库存
        saleStock(stock);
        //创建订单
        int id = createOrder(stock);
        return id;
    }

    @Override
    public int createOptimisticOrder(int sid) {
        //校验库存
        Stock stock = checkStock(sid);
        //乐观锁更新库存
        boolean success = saleStockOptimistic(stock);
        if (!success) {
            throw new RuntimeException("过期库存值，更新失败");
        }
        //创建订单
        createOrder(stock);
        return stock.getCount() - (stock.getSale() + 1);
    }

    /**
     * 数据库扣库存、订单记录写入数据库，同一个事务，原子操作
     * 队列异步去创建订单
     *
     * @param sid
     * @param userId
     * @return
     */
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    @Override
    public int createOptimisticOrder(int sid, Long userId) {
        // 检查用户合法性
        User user = userMapper.selectByPrimaryKey((long) userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        //校验库存
        Stock stock = checkStock(sid);
        //乐观锁更新库存
        // 此次买10张：update ticket = 490 where ticket_id = today and ticket_count=500
        boolean success = saleStockOptimistic(stock);
        if (!success) {
            throw new RuntimeException("过期库存值，更新失败");
        }

//        LOGGER.info("用户信息验证成功：[{}]", user.toString());

        // 首先写入订单记录表
        UUID orderId = UUID.randomUUID();
        OrderRecord record = new OrderRecord();
        record.setOrderId(orderId.toString());
        record.setSid(sid);
        record.setUserId(userId);
        record.setStatus(OrderRecordStatus.NOT_SEND.getStatus());
        // 队列发送的订单，默认没有重发
        record.setCount(0);
        record.setCreateTime(new Date());
        orderService.insertOrderRecord(record);


        //异步创建订单
        try {
            createOnlyOrderByMq(orderId.toString(), sid, userId);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return stock.getCount() - (stock.getSale() + 1);
    }

    /**
     * 关键点：同一个事务内的锁共享
     * 事务隔离性：
     * 整个方法在一个事务中执行
     * 事务内的所有操作共享同一个锁
     * 锁的粒度：
     * 锁的是行级别，不是方法级别
     * 同一个事务内的多个操作可以访问同一行
     * 锁的持续时间：
     * 从 SELECT ... FOR UPDATE 开始
     * 到事务提交或回滚结束
     * @param sid
     * @return
     */
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    @Override
    public int createPessimisticOrder(int sid) {
        //校验库存(悲观锁for update)
        Stock stock = checkStockForUpdate(sid);
        //更新库存
        saleStock(stock);
        //创建订单
        createOrder(stock);

//        // TODO 异步下单
//        createOrderByMq(sid, user);

        return stock.getCount() - (stock.getSale());
    }

    @Override
    public int createVerifiedOrder(Integer sid, Long userId, String verifyHash) throws Exception {

        // 验证是否在抢购时间内
        LOGGER.info("请自行验证是否在抢购时间内,假设此处验证成功");

        // 验证hash值合法性
        String hashKey = CacheKey.HASH_KEY.getKey() + "_" + sid + "_" + userId;
        System.out.println(hashKey);
        String verifyHashInRedis = stringRedisTemplate.opsForValue().get(hashKey);
        if (!verifyHash.equals(verifyHashInRedis)) {
            throw new Exception("hash值与Redis中不符合");
        }
        LOGGER.info("验证hash值合法性成功");

        // 检查用户合法性
        User user = userMapper.selectByPrimaryKey(userId.longValue());
        if (user == null) {
            throw new Exception("用户不存在");
        }
        LOGGER.info("用户信息验证成功：[{}]", user.toString());

        // 检查商品合法性
        Stock stock = stockService.getStockById(sid);
        if (stock == null) {
            throw new Exception("商品不存在");
        }
        LOGGER.info("商品信息验证成功：[{}]", stock.toString());

        //乐观锁更新库存
        boolean success = saleStockOptimistic(stock);
        if (!success) {
            throw new RuntimeException("过期库存值，更新失败");
        }
        LOGGER.info("乐观锁更新库存成功");

        //创建订单
        createOrderWithUserInfoInDB(stock, userId);
        LOGGER.info("创建订单成功");

        return stock.getCount() - (stock.getSale() + 1);
    }

    @Override
    public void createOrderByMq(Integer sid, Long userId) throws Exception {

//        // 模拟多个用户同时抢购，导致消息队列排队等候10秒
//        Thread.sleep(10000);

        Stock stock;
        //校验库存（不要学我在trycatch中做逻辑处理，这样是不优雅的。这里这样处理是为了兼容之前的秒杀系统文章）
        try {
            stock = checkStock(sid);
        } catch (Exception e) {
            LOGGER.info("库存不足！");
            return;
        }
        //乐观锁更新库存
        boolean updateStock = saleStockOptimistic(stock);
        if (!updateStock) {
            LOGGER.warn("扣减库存失败，库存已经为0");
            return;
        }

        LOGGER.info("扣减库存成功，剩余库存：[{}]", stock.getCount() - stock.getSale() - 1);
        stockService.delStockCountCache(sid);
        LOGGER.info("删除库存缓存");

        //创建订单
        LOGGER.info("写入订单至数据库");
        createOrderWithUserInfoInDB(stock, userId);
        LOGGER.info("写入订单至缓存供查询");
        createOrderWithUserInfoInCache(stock, userId);
        LOGGER.info("下单完成");

    }

    // 默认开启一个事务
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    @Override
    public void createOrderByMq(String orderId, Integer sid, Long userId) throws Exception {
//        //创建订单
//        LOGGER.info("写入订单至数据库");
//        Stock stock = checkStock(sid);
//        createOrderWithUserInfoInDB(orderId, stock, userId);
//
//        // 修改订单记录状态
//        updateOrderRecordStatus(orderId);
//
//        LOGGER.info("写入订单至缓存供查询"); // TODO 抢购时就加入缓存，还是下单时加入？
//        createOrderWithUserInfoInCache(stock, userId);
//        LOGGER.info("下单完成");

        Stock stock;
        //校验库存（不要学我在trycatch中做逻辑处理，这样是不优雅的。这里这样处理是为了兼容之前的秒杀系统文章）
        try {
            stock = checkStock(sid);
        } catch (Exception e) {
            LOGGER.info("库存不足！");
            return;
        }
        //乐观锁更新库存
        boolean updateStock = saleStockOptimistic(stock);
        if (!updateStock) {
            LOGGER.warn("扣减库存失败，库存已经为0");
            return;
        }

        LOGGER.info("扣减库存成功，剩余库存：[{}]", stock.getCount() - stock.getSale() - 1);
        stockService.delStockCountCache(sid);
        LOGGER.info("删除库存缓存");

        //创建订单
        LOGGER.info("写入订单至数据库");
        createOrderWithUserInfoInDB(stock, userId);
        LOGGER.info("写入订单至缓存供查询");
        createOrderWithUserInfoInCache(stock, userId);
        LOGGER.info("下单完成");
    }

    /**
     * todo
     * 只创建订单
     * 先记录下单信息order_message，状态 代下发
     * 然后消息队列去创建消息，创建成功后，线程异步去更新 order_message为成功
     * 下单重试三次一直失败，则退回票数（悲观锁）
     *
     * @param sid
     * @param userId
     * @throws Exception
     */
    public void createOnlyOrderByMq(String orderId, Integer sid, Long userId) throws Exception {

        // 模拟多个用户同时抢购，导致消息队列排队等候10秒
        Thread.sleep(10000);

        // TODO 创建订单信息 order_mesage，代下发状态，保存到数据库

        // 异步创建订单
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("orderId", orderId);
        jsonObject.put("sid", sid);
        jsonObject.put("userId", userId);
        sendToOrderQueue(jsonObject.toString());

    }

    public void updateOrderRecordStatus (String orderId) {
        OrderRecord record = new OrderRecord();
        record.setOrderId(orderId);
        record.setStatus(OrderRecordStatus.SEND_SUCCESS.getStatus());
        // 队列发送的订单，默认没有重发
        record.setUpdateTime(new Date());
        orderService.updateOrderRecordStatus(record);
    }

    /**
     * 向消息队列orderQueue发送消息
     *
     * @param message
     */
    private void sendToOrderQueue(String message) {
        LOGGER.info("这就去通知消息队列开始下单：[{}]", message);
        this.rabbitTemplate.convertAndSend("orderQueue", message);
    }

    @Override
    public Boolean checkUserOrderInfoInCache(Integer sid, Long userId) throws Exception {
        String key = CacheKey.USER_HAS_ORDER.getKey() + "_" + sid;
        LOGGER.info("检查用户Id：[{}] 是否抢购过商品Id：[{}] 检查Key：[{}]", userId, sid, key);
        return stringRedisTemplate.opsForSet().isMember(key, userId.toString());
    }

    /**
     * 检查库存
     *
     * @param sid
     * @return
     */
    private Stock checkStock(int sid) {
        // todo 比较库存是否满足此次 票数
        Stock stock = stockService.getStockById(sid);
        if (stock.getSale().equals(stock.getCount())) {
            throw new RuntimeException("库存不足");
        }
        return stock;
    }

    /**
     * 检查库存 ForUpdate
     *
     * @param sid
     * @return
     */
    private Stock checkStockForUpdate(int sid) {
        Stock stock = stockService.getStockByIdForUpdate(sid);
        if (stock.getSale().equals(stock.getCount())) {
            throw new RuntimeException("库存不足");
        }
        return stock;
    }

    /**
     * 更新库存
     *
     * @param stock
     */
    private void saleStock(Stock stock) {
        stock.setSale(stock.getSale() + 1);
        stockService.updateStockById(stock);
    }

    /**
     * 更新库存 乐观锁
     *
     * @param stock
     */
    private boolean saleStockOptimistic(Stock stock) {
        LOGGER.info("查询数据库，尝试更新库存");
        int count = stockService.updateStockByOptimistic(stock);
        return count != 0;
    }

    private void createOrderRecord() {

    }

    /**
     * 创建订单
     *
     * @param stock
     * @return
     */
    private int createOrder(Stock stock) {
        StockOrder order = new StockOrder();
        order.setSid(stock.getId());
        order.setName(stock.getName());
        return orderMapper.insertSelective(order);
    }

    /**
     * 创建订单：保存用户订单信息到数据库
     *
     * @param stock
     * @return
     */
    private int createOrderWithUserInfoInDB(Stock stock, Long userId) {
        StockOrder order = new StockOrder();
        order.setSid(stock.getId());
        order.setName(stock.getName());
        order.setUserId(userId);
        return orderMapper.insertSelective(order);
    }

    private int createOrderWithUserInfoInDB(String orderId, Stock stock, Long userId) {
        StockOrder order = new StockOrder();
        order.setOrderId(orderId);
        order.setSid(stock.getId());
        order.setName(stock.getName());
        order.setUserId(userId);
        return orderMapper.insertSelective(order);
    }

    /**
     * 创建订单：保存用户订单信息到缓存
     *
     *         // 为key设置8小时过期时间
     *         // 用户离开，则删除缓存
     *         // 定时任务删除
     * @param stock
     * @return 返回添加的个数
     */
    private Long createOrderWithUserInfoInCache(Stock stock, Long userId) {
        String key = CacheKey.USER_HAS_ORDER.getKey() + "_" + stock.getId().toString();
        LOGGER.info("写入用户订单数据Set：[{}] [{}]", key, userId.toString());
//        return stringRedisTemplate.opsForSet().add(key, userId.toString());

        Long result = stringRedisTemplate.opsForSet().add(key, userId.toString());
        // 为key设置8小时过期时间
        // 用户离开，则删除缓存
        // 定时任务删除
        stringRedisTemplate.expire(key, 12, TimeUnit.HOURS);
        return result;
    }

    // 每天定时删除CacheKey.USER_HAS_ORDER 缓存


    @Override
    public void insertOrderRecord(OrderRecord orderRecord) {
        if (null != orderRecord) {
            return;
        }
        orderMapper.insertOrderRecord(orderRecord);
    }

    public List<OrderRecord> selectOrderRecordList(OrderRecord orderRecord) {
        if (null != orderRecord) {
            return null;
        }
        return orderMapper.selectOrderRecordList(orderRecord);
    }

    @Override
    public List<OrderRecord> selectOrderRecordNotSend(OrderRecord orderRecord) {
        if (null != orderRecord) {
            return null;
        }
        return orderMapper.selectOrderRecordNotSend(orderRecord);
    }

    @Override
    public Integer updateOrderRecordStatus(OrderRecord orderRecord) {
        if (null != orderRecord && StringUtils.isEmpty(orderRecord.getOrderId())) {
            return 0;
        }
        return orderMapper.updateOrderRecordStatus(orderRecord);
    }

    @Override
    public Integer updateOrderRecordCount(OrderRecord orderRecord) {
        return orderMapper.updateOrderRecordCount(orderRecord);
    }
}
