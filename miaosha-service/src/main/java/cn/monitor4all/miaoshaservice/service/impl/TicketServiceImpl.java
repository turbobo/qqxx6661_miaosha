package cn.monitor4all.miaoshaservice.service.impl;

import cn.monitor4all.miaoshadao.dao.TicketEntity;
import cn.monitor4all.miaoshadao.dao.TicketPurchaseRecord;
import cn.monitor4all.miaoshadao.dao.TicketOrder;
import cn.monitor4all.miaoshadao.dao.User;
import cn.monitor4all.miaoshadao.mapper.TicketEntityMapper;
import cn.monitor4all.miaoshadao.mapper.TicketPurchaseRecordMapper;
import cn.monitor4all.miaoshadao.mapper.TicketOrderMapper;
import cn.monitor4all.miaoshadao.model.*;
import cn.monitor4all.miaoshadao.utils.CacheKey;
import cn.monitor4all.miaoshaservice.service.*;
import cn.monitor4all.miaoshaservice.utils.redis.CacheExpiredTime;
import cn.monitor4all.miaoshaservice.utils.redis.RedisCache;
import cn.monitor4all.miaoshaservice.utils.redis.RedisLock;
import com.alibaba.fastjson.JSON;
import com.google.common.util.concurrent.RateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;

import javax.annotation.PostConstruct;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
public class TicketServiceImpl implements TicketService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TicketServiceImpl.class);

    // 抢购时间常量
    private static final LocalTime START_TIME = LocalTime.of(8, 0); // 上午9点开始
    private static final LocalTime END_TIME = LocalTime.of(18, 0);  // 晚上11点结束

    @Resource
    private TicketEntityMapper ticketEntityMapper;

    @Resource
    private TicketPurchaseRecordMapper ticketPurchaseRecordMapper;

    @Resource
    private TicketOrderMapper ticketOrderMapper;

    @Resource
    private TicketCacheManager ticketCacheManager;

    @Resource
    private MiaoshaStatusService miaoshaStatusService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private ValidationService validationService;

    @Resource
    private UserService userService;

    @Resource
    private TicketCodeGeneratorService ticketCodeGeneratorService;

    @Resource
    private RabbitTemplate rabbitTemplate;


    // Guava令牌桶：每秒放行10个请求
    RateLimiter rateLimiter = RateLimiter.create(1000);


    public TicketServiceImpl() {
        // 构造函数中不进行初始化，等待依赖注入完成
    }

    /**
     * 在依赖注入完成后初始化票券数据
     */
    @PostConstruct
    public void init() {
        try {
            // 检查依赖注入是否成功
            if (ticketEntityMapper == null) {
                LOGGER.error("TicketEntityMapper 依赖注入失败");
                return;
            }
            if (ticketPurchaseRecordMapper == null) {
                LOGGER.error("TicketPurchaseRecordMapper 依赖注入失败");
                return;
            }
            if (ticketCacheManager == null) {
                LOGGER.error("TicketCacheManager 依赖注入失败");
                return;
            }

            LOGGER.info("依赖注入检查完成，开始初始化票券数据");
            initializeTickets();
        } catch (Exception e) {
            LOGGER.error("初始化失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 初始化票券数据到Redis
     */
    private void initializeTickets() {
        try {
            LOGGER.info("开始初始化票券数据到Redis");

            // 再次检查依赖注入
            if (ticketEntityMapper == null) {
                LOGGER.error("TicketEntityMapper 为空，无法初始化");
                return;
            }

            // 从数据库查询最近3天的票券数据
            LocalDate today = LocalDate.now();
            String todayStr = today.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            String tomorrowStr = today.plusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            String dayAfterTomorrowStr = today.plusDays(2).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

            List<TicketEntity> dbTickets = ticketEntityMapper.selectRecentTickets(todayStr, dayAfterTomorrowStr);

            if (dbTickets.isEmpty()) {
                LOGGER.info("数据库中没有票券数据，创建默认数据");
                // 创建默认票券数据
                createDefaultTickets();
                dbTickets = ticketEntityMapper.selectRecentTickets(todayStr, dayAfterTomorrowStr);
            }

            // 将数据库数据转换为前端模型并放入Redis
            List<Ticket> ticketList = new ArrayList<>();
            for (TicketEntity dbTicket : dbTickets) {
                Ticket ticket = new Ticket(dbTicket.getDate(), dbTicket.getTotalCount());
                ticket.setRemaining(dbTicket.getRemainingCount());

                // 将票券放入Redis缓存
//                ticketCacheManager.saveTicket(dbTicket.getDate(), ticket);

                ticketList.add(ticket);
            }

            // 将票券列表放入Redis缓存
            ticketCacheManager.saveTicketList(ticketList);

            LOGGER.info("票券数据初始化完成，共{}张票券", ticketList.size());

        } catch (Exception e) {
            LOGGER.error("初始化票券数据到Redis失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 创建默认票券数据
     */
    private void createDefaultTickets() {
        try {
            LocalDate today = LocalDate.now();
            String todayStr = today.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            String tomorrowStr = today.plusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            String dayAfterTomorrowStr = today.plusDays(2).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

            // 创建今日票券
            createTicket(todayStr, "今日票券", 100);
            // 创建明日票券
            createTicket(tomorrowStr, "明日票券", 150);
            // 创建后日票券
            createTicket(dayAfterTomorrowStr, "后日票券", 200);

            LOGGER.info("默认票券数据创建完成");
        } catch (Exception e) {
            LOGGER.error("创建默认票券数据失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 根据日期获取库存数量
     *
     * @param date 日期
     * @return 库存数量
     */
    private int getStockForDate(String date) {
        try {
            Ticket ticket = ticketCacheManager.getTicketWithFallback(date);
            if (ticket != null) {
                return ticket.getRemaining();
            }

            // 如果Redis中没有，从数据库查询
            TicketEntity dbTicket = ticketEntityMapper.selectByDate(date);
            if (dbTicket != null) {
                return dbTicket.getRemainingCount();
            }

            // 默认库存数量
            if (date.equals(LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))) {
                return 100; // 今天
            } else if (date.equals(LocalDate.now().plusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))) {
                return 150; // 明天
            } else {
                return 200; // 后天
            }
        } catch (Exception e) {
            LOGGER.error("获取库存数量失败，日期: {}", date, e);
            return 0;
        }
    }

    /**
     * 根据日期查询票券信息
     *
     * @param date 日期
     * @return 票券信息
     */
    public Ticket getTicketByDate(String date) {
        try {
            // 先从Redis获取
            Ticket ticket = ticketCacheManager.getTicketWithFallback(date);
            if (ticket != null) {
                LOGGER.info("从Redis获取票券，日期: {}", date);
                return ticket;
            }

            // Redis中没有，从数据库查询
            TicketEntity dbTicket = ticketEntityMapper.selectByDate(date);
            if (dbTicket != null) {
                Ticket ticketModel = new Ticket(dbTicket.getDate(), dbTicket.getTotalCount());
                ticketModel.setRemaining(dbTicket.getRemainingCount());

                // 保存到Redis
                ticketCacheManager.saveTicket(date, ticketModel);
                LOGGER.info("从数据库获取票券，日期: {}, 并保存到Redis", date);
                return ticketModel;
            }
            return null;
        } catch (Exception e) {
            LOGGER.error("根据日期查询票券失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 查询所有票券信息
     *
     * @return 票券列表
     */
    public List<Ticket> getAllTickets() {
        try {
            // 先从Redis获取
            List<Ticket> cachedTickets = ticketCacheManager.getTicketList();
            if (cachedTickets != null && !cachedTickets.isEmpty()) {
                LOGGER.info("从Redis获取所有票券");
                return cachedTickets;
            }

            // Redis中没有，从数据库查询
            List<TicketEntity> dbTickets = ticketEntityMapper.selectAllActiveTickets();
            List<Ticket> result = new ArrayList<>();
            for (TicketEntity dbTicket : dbTickets) {
                Ticket ticket = new Ticket(dbTicket.getDate(), dbTicket.getTotalCount());
                ticket.setRemaining(dbTicket.getRemainingCount());
                result.add(ticket);
            }

            // 保存到Redis
            ticketCacheManager.saveTicketList(result);
            LOGGER.info("从数据库获取所有票券，并保存到Redis");
            return result;
        } catch (Exception e) {
            LOGGER.error("查询所有票券失败: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * 更新票券库存
     *
     * @param date              日期
     * @param newRemainingCount 新的剩余数量
     * @return 是否更新成功
     */
    @Transactional
    public boolean updateTicketStock(String date, int newRemainingCount) {
        try {
            TicketEntity dbTicket = ticketEntityMapper.selectByDateForUpdate(date);
            if (dbTicket == null) {
                LOGGER.warn("票券不存在，日期: {}", date);
                return false;
            }

            if (newRemainingCount < 0 || newRemainingCount > dbTicket.getTotalCount()) {
                LOGGER.warn("库存数量无效，日期: {}, 新数量: {}, 总数量: {}",
                        date, newRemainingCount, dbTicket.getTotalCount());
                return false;
            }

            int oldRemainingCount = dbTicket.getRemainingCount();
            int soldCount = dbTicket.getTotalCount() - newRemainingCount;

            dbTicket.setRemainingCount(newRemainingCount);
            dbTicket.setSoldCount(soldCount);
            dbTicket.setVersion(dbTicket.getVersion() + 1);

            int result = ticketEntityMapper.updateByPrimaryKey(dbTicket);
            if (result > 0) {
                // 更新Redis缓存
                ticketCacheManager.deleteTicket(date);

                // 更新票券列表缓存
                List<Ticket> ticketList = ticketCacheManager.getTicketList();
                if (ticketList != null) {
                    ticketList.removeIf(t -> t.getDate().equals(date));
                    Ticket updatedTicket = new Ticket(date, dbTicket.getTotalCount());
                    updatedTicket.setRemaining(newRemainingCount);
                    ticketList.add(updatedTicket);
                    ticketCacheManager.saveTicketList(ticketList);
                }

                LOGGER.info("票券库存更新成功，日期: {}, 旧库存: {}, 新库存: {}",
                        date, oldRemainingCount, newRemainingCount);
                return true;
            }
            return false;
        } catch (Exception e) {
            LOGGER.error("更新票券库存失败，日期: {}, 新数量: {}", date, newRemainingCount, e);
            return false;
        }
    }

    /**
     * 创建新票券
     *
     * @param date       日期
     * @param name       票券名称
     * @param totalCount 总数量
     * @return 是否创建成功
     */
    @Transactional
    public boolean createTicket(String date, String name, int totalCount) {
        try {
            // 检查票券是否已存在
            TicketEntity existingTicket = ticketEntityMapper.selectByDate(date);
            if (existingTicket != null) {
                LOGGER.warn("票券已存在，日期: {}", date);
                return false;
            }

            // 创建新的票券实体
            TicketEntity newTicket = new TicketEntity();
            newTicket.setDate(date);
            newTicket.setName(name);
            newTicket.setTotalCount(totalCount);
            newTicket.setRemainingCount(totalCount);
            newTicket.setSoldCount(0);
            newTicket.setVersion(1);
            newTicket.setStatus(1);

            int result = ticketEntityMapper.insert(newTicket);
            if (result > 0) {
                LOGGER.info("票券创建成功，日期: {}, 名称: {}, 数量: {}", date, name, totalCount);

                // 创建成功后，更新Redis缓存
                Ticket ticket = new Ticket(date, totalCount);
                ticketCacheManager.saveTicket(date, ticket);

                // 更新票券列表缓存
                List<Ticket> ticketList = ticketCacheManager.getTicketList();
                if (ticketList != null) {
                    ticketList.add(ticket);
                    ticketCacheManager.saveTicketList(ticketList);
                }

                return true;
            }
            return false;
        } catch (Exception e) {
            LOGGER.error("创建票券失败，日期: {}, 名称: {}, 数量: {}", date, name, totalCount, e);
            return false;
        }
    }

    /**
     * 删除票券
     *
     * @param date 日期
     * @return 是否删除成功
     */
    @Transactional
    public boolean deleteTicket(String date) {
        try {
            int result = ticketEntityMapper.deleteByDate(date);
            if (result > 0) {
                // 更新Redis缓存
                ticketCacheManager.deleteTicket(date);

                // 更新票券列表缓存
                List<Ticket> ticketList = ticketCacheManager.getTicketList();
                if (ticketList != null) {
                    ticketList.removeIf(t -> t.getDate().equals(date));
                    ticketCacheManager.saveTicketList(ticketList);
                }

                LOGGER.info("票券删除成功，日期: {}", date);
                return true;
            }
            return false;
        } catch (Exception e) {
            LOGGER.error("删除票券失败，日期: {}", date, e);
            return false;
        }
    }

    /**
     * 获取票券统计信息
     *
     * @return 统计信息Map
     */
    public Map<String, Object> getTicketStatistics() {
        try {
            List<TicketEntity> allTickets = ticketEntityMapper.selectAllActiveTickets();
            Map<String, Object> stats = new HashMap<>();

            int totalTickets = 0;
            int totalSold = 0;
            int totalRemaining = 0;

            for (TicketEntity ticket : allTickets) {
                totalTickets += ticket.getTotalCount();
                totalSold += ticket.getSoldCount();
                totalRemaining += ticket.getRemainingCount();
            }

            stats.put("totalTickets", totalTickets);
            stats.put("totalSold", totalSold);
            stats.put("totalRemaining", totalRemaining);
            stats.put("sellRate", totalTickets > 0 ? (double) totalSold / totalTickets * 100 : 0);
            stats.put("ticketCount", allTickets.size());

            return stats;
        } catch (Exception e) {
            LOGGER.error("获取票券统计信息失败: {}", e.getMessage(), e);
            return new HashMap<>();
        }
    }

    @Override
    public List<Ticket> getRecentTickets() {
        try {
            // 从Redis获取最近3天的票券数据
            List<Ticket> cachedTickets = ticketCacheManager.getTicketList();
            if (cachedTickets != null && cachedTickets.size() != 3) {
                LOGGER.info("从Redis获取最近3天的票券");
                return cachedTickets;
            }

            // 缓存数据不完整，从数据库获取
            // 计算最近3天的日期范围
            LocalDate today = LocalDate.now();
            String todayStr = today.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            String tomorrowStr = today.plusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            String dayAfterTomorrowStr = today.plusDays(2).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

            // 从数据库查询最近3天的票券数据
            List<TicketEntity> dbTickets = ticketEntityMapper.selectRecentTickets(todayStr, dayAfterTomorrowStr);

            // 将数据库实体转换为前端模型
            List<Ticket> result = new ArrayList<>();
            for (TicketEntity dbTicket : dbTickets) {
                // 将String日期转换为LocalDate
                Ticket ticket = new Ticket(dbTicket.getDate(), dbTicket.getTotalCount());
                ticket.setRemaining(dbTicket.getRemainingCount());
                result.add(ticket);
            }

            // 如果数据库中没有数据，则初始化一些默认数据
            if (result.isEmpty()) {
                LOGGER.info("数据库中无票券数据，初始化默认数据");
                initializeTickets(); // 重新调用initializeTickets，它会从数据库获取数据
                result = Arrays.asList(
                        ticketCacheManager.getTicketWithFallback(todayStr),
                        ticketCacheManager.getTicketWithFallback(tomorrowStr),
                        ticketCacheManager.getTicketWithFallback(dayAfterTomorrowStr)
                );
            }

            // 更新Redis缓存
            ticketCacheManager.saveTicketList(result);

            LOGGER.info("从数据库获取到{}张票券", result.size());
            return result;

        } catch (Exception e) {
            LOGGER.error("从数据库查询票券数据失败: {}", e.getMessage(), e);
            // 如果数据库查询失败，回退到Redis缓存
            LocalDate today = LocalDate.now();
            String todayStr = today.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            return Arrays.asList(
                    ticketCacheManager.getTicketWithFallback(todayStr),
                    ticketCacheManager.getTicketWithFallback(today.plusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))),
                    ticketCacheManager.getTicketWithFallback(today.plusDays(2).format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))
            );
        }
    }

    // 默认开启一个事务
    @Override
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public ApiResponse<PurchaseRecord> purchaseTicket(PurchaseRequest request) throws Exception {

        multiValidParam(request);

        // 获取请求参数
        Long userId = request.getUserId();
        String purchaseDate = request.getDate();

        // 接口限流
        // 3、用户、票数校验
        // 4、悲观锁更新票券库存
        // 5、生成订单 ----> 同步创建访客预约记录，下发门禁等设备权限，通过rpc，失败则写入重试表；仍然失败，管理员可以手动创建访客预约
        // 6、返回选购成功
        // 7、查询订单


        // TODO  //订单锁 新锁定同一时间内相同订单只有一个线程在创建或者更新
        final String lockKey = String.format("{}##{}", request.getUserId(), request.getDate());
        RedisLock redisLock = RedisCache.createRedisLock(lockKey, CacheExpiredTime.ONE_MINUTE, 1000);
        if (null != redisLock && redisLock.lock()) {
            // 从数据库获取票券信息（使用悲观锁）
            PurchaseRecord purchaseRecord = doPurchaseTicketWithPessimisticLockV2(request);

            LOGGER.info("用户{}成功购买{}的票券，票券编号：{}", userId, purchaseDate, purchaseRecord.getTicketCode());
            return ApiResponse.success(purchaseRecord);
        } else {
            throw new BusinessException("获取订单创建锁超时");
        }

       /* TicketEntity ticketEntity = ticketEntityMapper.selectByDateForUpdate(purchaseDate);
        if (ticketEntity == null) {
            throw new IllegalStateException("该日期的票券不存在");
        }

        // 检查是否售罄
        if (ticketEntity.getRemainingCount() <= 0) {
            throw new IllegalStateException("该日期的票券已售罄");
        }

        // 更新库存（乐观锁）
        ticketEntity.setRemainingCount(ticketEntity.getRemainingCount() - 1);
        ticketEntity.setSoldCount(ticketEntity.getSoldCount() + 1);
        boolean updated = updateTicketStockByOptimistic(ticketEntity);
        if (!updated) {
            throw new IllegalStateException("票券购买失败，请重试 (库存版本冲突)");
        }

        // 生成票券编号
        String ticketCode = generateTicketCode(userId.toString(), purchaseDate);

        // 保存购买记录到数据库
        TicketPurchaseRecord recordEntity = new TicketPurchaseRecord(userId, purchaseDate, ticketCode);
        recordEntity.setTicketId(ticketEntity.getId());
        recordEntity.setOrderId(UUID.randomUUID().toString());
        boolean recordSaved = savePurchaseRecord(recordEntity);
        if (!recordSaved) {
            throw new IllegalStateException("购买记录保存失败");
        }

        // 更新Redis缓存
        ticketCacheManager.deleteTicket(purchaseDate);

        // 更新票券列表缓存
        List<Ticket> ticketList = ticketCacheManager.getTicketList();
        if (ticketList != null) {
            ticketList.removeIf(t -> t.getDate().equals(purchaseDate));
            Ticket updatedTicket = new Ticket(purchaseDate, ticketEntity.getTotalCount());
            updatedTicket.setRemaining(ticketEntity.getRemainingCount() - 1);
            ticketList.add(updatedTicket);
            ticketCacheManager.saveTicketList(ticketList);
        }

        // 返回前端模型
        PurchaseRecord record = new PurchaseRecord(userId, LocalDate.parse(purchaseDate), ticketCode);
        ticketCacheManager.addPurchaseRecord(userId, purchaseDate, record);*/

    }

    /**
     * 用户请求 → 限流检查 → 参数校验 → 后续步骤（时间校验/库存扣减等）
     *
     * @param request
     * @return
     */
    // 默认开启一个事务
    @Override
    public ApiResponse<PurchaseRecord> purchaseTicketV1WithOptimisticLock(PurchaseRequest request) throws Exception {

        multiValidParam(request);

        // 获取请求参数
        Long userId = request.getUserId();
        String purchaseDate = request.getDate();

        // TODO  //订单锁 新锁定同一时间内相同订单只有一个线程在创建或者更新
        final String lockKey = String.format("{}##{}", request.getUserId(), request.getDate());
        RedisLock redisLock = RedisCache.createRedisLock(lockKey, CacheExpiredTime.ONE_MINUTE, 1000);
        if (null != redisLock && redisLock.lock()) {
            PurchaseRecord purchaseRecord = doPurchaseTicketWithOptimisticLock(request);

            LOGGER.info("用户{}成功购买{}的票券，票券编号：{}", userId, purchaseDate, purchaseRecord.getTicketCode());
            return ApiResponse.success(purchaseRecord);
        } else {
            throw new BusinessException("获取订单创建锁超时");
        }


    }

    @Override
    public ApiResponse<Map<String, Object>> purchaseTicketV2(PurchaseRequest request) {
        try {
            LOGGER.info("开始异步抢购流程，用户ID: {}, 日期: {}", request.getUserId(), request.getDate());

            // 参数校验
            multiValidParam(request);

            // 生成请求ID
            String requestId = generateRequestId(request.getUserId(), request.getDate());

            // 将请求封装后发送到MQ队列
            Map<String, Object> message = new HashMap<>();
            message.put("requestId", requestId);
            message.put("userId", request.getUserId());
            message.put("date", request.getDate());
            message.put("verifyHash", request.getVerifyHash());
            message.put("timestamp", System.currentTimeMillis());

            // 发送到MQ队列
            rabbitTemplate.convertAndSend("miaosha.purchase.exchange", "miaosha.purchase", message);

            LOGGER.info("异步抢购请求已发送到MQ队列，请求ID: {}", requestId);

            // 返回请求ID给前端
            Map<String, Object> result = new HashMap<>();
            result.put("requestId", requestId);
            result.put("message", "提交成功，正在排队");

            return ApiResponse.success(result);

        } catch (Exception e) {
            LOGGER.error("异步抢购失败，用户ID: {}, 日期: {}, 错误: {}",
                    request.getUserId(), request.getDate(), e.getMessage(), e);
            return ApiResponse.error(e.getMessage());
        }
    }

    @Override
    public ApiResponse<Map<String, Object>> getPurchaseResult(String requestId, Long userId, String date) {
        try {
            LOGGER.info("查询异步抢购结果，请求ID: {}, 用户ID: {}, 日期: {}", requestId, userId, date);

            // 检查是否已购买
            boolean hasPurchased = hasPurchased(userId, date);

            if (hasPurchased) {
                // 查询订单信息
                TicketOrder order = ticketOrderMapper.selectByUserIdAndDate(userId, date);
                if (order != null) {
                    Map<String, Object> result = new HashMap<>();
                    result.put("status", "SUCCESS");
                    result.put("requestId", requestId);
                    result.put("ticketCode", order.getTicketCode());
                    result.put("orderNo", order.getOrderNo());
                    result.put("message", "抢购成功");

                    LOGGER.info("异步抢购成功，请求ID: {}, 票券编码: {}", requestId, order.getTicketCode());
                    return ApiResponse.success(result);
                }
            }

            // 检查是否超时（5分钟）
            long currentTime = System.currentTimeMillis();
            long requestTime = getRequestTimeFromCache(requestId);
            if (requestTime > 0 && (currentTime - requestTime) > 5 * 60 * 1000) {
                Map<String, Object> result = new HashMap<>();
                result.put("status", "TIMEOUT");
                result.put("requestId", requestId);
                result.put("message", "排队超时，请稍后再试");

                LOGGER.warn("异步抢购超时，请求ID: {}", requestId);
                return ApiResponse.success(result);
            }

            // 还在排队中
            Map<String, Object> result = new HashMap<>();
            result.put("status", "QUEUED");
            result.put("requestId", requestId);
            result.put("message", "正在排队中，请稍后查询");

            return ApiResponse.success(result);

        } catch (Exception e) {
            LOGGER.error("查询异步抢购结果失败，请求ID: {}, 错误: {}", requestId, e.getMessage(), e);
            return ApiResponse.error("查询失败: " + e.getMessage());
        }
    }

    /**
     * 生成请求ID
     */
    private String generateRequestId(Long userId, String date) {
        return "REQ_" + userId + "_" + date + "_" + System.currentTimeMillis();
    }

    /**
     * 从缓存获取请求时间
     */
    private long getRequestTimeFromCache(String requestId) {
        try {
            String timeStr = stringRedisTemplate.opsForValue().get("request_time:" + requestId);
            return timeStr != null ? Long.parseLong(timeStr) : 0;
        } catch (Exception e) {
            LOGGER.warn("获取请求时间失败，请求ID: {}", requestId);
            return 0;
        }
    }

    /**
     * 请求参数校验
     *
     * @param request
     */
    private void multiValidParam(PurchaseRequest request) throws Exception {
        // *********入参为空校验********
        validNullParam(request);

        // *********合法性校验：抢购时间内、用户登录、token、黑名单等********
        validLegalParam(request);

        // 限流检查
        validRateLimit(request);
    }

    // 校验参数不能为空
    private void validNullParam(PurchaseRequest request) {
        if (Objects.isNull(request.getUserId()) || StringUtils.isEmpty(request.getDate()) || StringUtils.isEmpty(request.getVerifyHash())) {
            LOGGER.warn("请求参数: {}", JSON.toJSONString(request));
            throw new IllegalArgumentException("参数不完整");
        }
    }

    // 有效性校验
    private void validLegalParam(PurchaseRequest request) throws Exception {
        if (Objects.isNull(request.getUserId()) || StringUtils.isEmpty(request.getDate()) || StringUtils.isEmpty(request.getVerifyHash())) {
            throw new IllegalArgumentException("参数不完整");
        }

        // 检查当前时间是否处于抢购时间内
        validateCurrentTimeInPurchaseWindow();

        // 验证码、哈希值 校验
        // 验证hash值合法性
        String hashKey = CacheKey.HASH_KEY.getKey() + "_" + request.getDate() + "_" + request.getUserId();
        System.out.println(hashKey);
        String verifyHashInRedis = stringRedisTemplate.opsForValue().get(hashKey);
        if (!Objects.equals(request.getVerifyHash(), verifyHashInRedis)) {
            throw new IllegalArgumentException("hash值与Redis中不符合");
        }
        LOGGER.info("验证hash值合法性成功");

        // 用户校验
        validationService.validateUserWithException(request.getUserId());

        // 票券校验
        validationService.validateTicketCountWithException(request.getDate());

        // 检查秒杀活动状态
        try {
            if (miaoshaStatusService.isMiaoshaPaused()) {
                throw new IllegalStateException("秒杀活动正在维护中，请稍后再试");
            }
        } catch (Exception e) {
            LOGGER.warn("检查秒杀活动状态失败，继续执行: {}", e.getMessage());
        }

//        // 检查是否已经购买（从数据库查询）
//        if (hasPurchased(request.getUserId(), request.getDate())) {
//            throw new IllegalStateException("您已购买过当天的票券，每人每天限购一张");
//        }
    }

    /**
     * 限流检查
     * 秒杀开始前（倒计时阶段）：限制单用户每分钟最多 10-30 次请求（主要是页面刷新、倒计时同步等非抢购请求）。
     * 秒杀进行中（抢购按钮激活后）：限制单用户每秒最多 1-2 次请求，或每分钟最多 20-50 次请求。
     * 例如：单用户 5 秒内只能发起 1 次抢购请求（通过 Redis 记录user:limit:userId的时间戳，超过则拦截）。
     * <p>
     * 用户级限流：秒杀中控制在 1-2 次 / 秒，核心是防恶意请求，保证公平。
     * 接口级限流：低库存场景按 “库存 ×10-20 倍”，高库存场景按 “系统承载的 70%-80%”，核心是匹配系统能 ---- 博物馆 2000张票
     */
    private void validRateLimit(PurchaseRequest request) {
        // 用户限流：每个用户，1分钟20次抢购
        boolean allowed = userService.isAllowed(request.getUserId(), 20, 60);
        if (!allowed) {
            throw new RuntimeException("购买失败，超过频率限制");
        }

        // 接口限流 每秒放行5000个请求
        // 单机使用 非阻塞式获取令牌，分布式使用redis+lua脚本限流
        if (!rateLimiter.tryAcquire(1000, TimeUnit.MILLISECONDS)) {
            LOGGER.warn("你被限流了，真不幸，直接返回失败");
            throw new RuntimeException("你被限流了，真不幸，直接返回失败");
        }
    }

    /**
     * 检查用户是否已经购买过该日期的票券
     * 先查询购买记录缓存，如果有则返回true，否则查询数据库，如果有则返回true，否则返回false
     *
     * @param userId 用户ID
     * @param date   日期
     * @return
     */
    @Override
    public boolean hasPurchased(Long userId, String date) {
        try {
            // 1. 先从缓存获取是否有该日期的购买记录
            PurchaseRecord cachedRecord = ticketCacheManager.getPurchaseRecord(userId, date);
            if (cachedRecord != null) {
                LOGGER.debug("从缓存获取到用户购买记录，用户ID: {}, 日期: {}, 票券编码: {}",
                        userId, date, cachedRecord.getTicketCode());
                return true;
            }

            LOGGER.debug("缓存中未找到用户购买记录，尝试从数据库查询，用户ID: {}, 日期: {}", userId, date);

            // 2. 缓存中没有，从数据库的ticket_order表查询用户ID+date的订单
            TicketOrder ticketOrder = ticketOrderMapper.selectByUserIdAndDate(userId, date);
            if (ticketOrder != null) {
                LOGGER.info("从数据库ticket_order表查询到用户购买记录，用户ID: {}, 日期: {}, 订单号: {}",
                        userId, date, ticketOrder.getOrderNo());

                // 将数据库记录同步到缓存
                PurchaseRecord record = new PurchaseRecord(
                        ticketOrder.getUserId(),
                        LocalDate.parse(ticketOrder.getTicketDate()),
                        ticketOrder.getTicketCode()
                );
                ticketCacheManager.addPurchaseRecord(userId, date, record);

                return true;
            }

            LOGGER.debug("数据库中未找到用户购买记录，用户ID: {}, 日期: {}", userId, date);
            return false;

        } catch (Exception e) {
            LOGGER.error("查询用户购买记录失败，用户ID: {}, 日期: {}, 错误: {}", userId, date, e.getMessage(), e);
            return false;
        }
    }

    @Override
    public Map<String, Object> getPurchaseTime(String date) {
        Map<String, Object> result = new HashMap<>();

        try {
            // 基本参数验证
            if (date == null || date.isEmpty()) {
                result.put("valid", false);
                result.put("message", "日期不能为空");
                result.put("code", "INVALID_DATE");
                return result;
            }

            // 解析日期
            LocalDate ticketDate;
            try {
                ticketDate = LocalDate.parse(date);
            } catch (Exception e) {
                result.put("valid", false);
                result.put("message", "日期格式无效，请使用yyyy-MM-dd格式");
                result.put("code", "INVALID_DATE_FORMAT");
                return result;
            }

            LocalDate today = LocalDate.now();
            LocalDate tomorrow = today.plusDays(1);
            LocalDate dayAfterTomorrow = today.plusDays(2);

            // 检查是否为过去日期
            if (ticketDate.isBefore(today)) {
                result.put("valid", false);
                result.put("message", "不能购买过去的日期");
                result.put("code", "PAST_DATE");
                result.put("ticketDate", date);
                result.put("currentDate", today.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
                return result;
            }

            // 检查是否为今天
            if (ticketDate.equals(today)) {
                result.put("valid", true);
                result.put("message", "可以抢购今日票券");
                result.put("code", "TODAY");
                result.put("ticketDate", date);
                result.put("currentDate", today.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
                result.put("timeType", "today");
                return result;
            }

            // 检查是否为明天
            if (ticketDate.equals(tomorrow)) {
                result.put("valid", true);
                result.put("message", "可以抢购明日票券");
                result.put("code", "TOMORROW");
                result.put("ticketDate", date);
                result.put("currentDate", today.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
                result.put("timeType", "tomorrow");
                return result;
            }

            // 检查是否为后天
            if (ticketDate.equals(dayAfterTomorrow)) {
                result.put("valid", true);
                result.put("message", "可以抢购后日票券");
                result.put("code", "DAY_AFTER_TOMORROW");
                result.put("ticketDate", date);
                result.put("currentDate", today.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
                result.put("timeType", "dayAfterTomorrow");
                return result;
            }

            // 检查是否为未来日期（超过3天）
            if (ticketDate.isAfter(dayAfterTomorrow)) {
                result.put("valid", false);
                result.put("message", "只能抢购最近3天的票券");
                result.put("code", "FUTURE_DATE");
                result.put("ticketDate", date);
                result.put("currentDate", today.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
                result.put("maxAllowedDate", dayAfterTomorrow.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
                return result;
            }

            // 默认情况
            result.put("valid", false);
            result.put("message", "未知的日期验证结果");
            result.put("code", "UNKNOWN");
            result.put("ticketDate", date);
            result.put("currentDate", today.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));

        } catch (Exception e) {
            LOGGER.error("验证抢购时间失败，日期: {}", date, e);
            result.put("valid", false);
            result.put("message", "验证抢购时间时发生错误: " + e.getMessage());
            result.put("code", "ERROR");
            result.put("ticketDate", date);
        }

        return result;
    }


    @Override
    public void validatePurchaseTime(String date) {
        validationService.validatePurchaseTimeWithException(date);
    }

    /**
     * 验证当前时间是否处于抢购时间窗口内
     * 抢购时间：每天上午9:00 - 晚上23:00
     *
     * @throws RuntimeException 如果当前时间不在抢购时间窗口内
     */
    private void validateCurrentTimeInPurchaseWindow() {
        LocalTime currentTime = LocalTime.now();

        // 检查当前时间是否在抢购时间窗口内
        if (currentTime.isBefore(START_TIME) || currentTime.isAfter(END_TIME)) {
            String errorMessage = String.format(
                    "当前时间不在抢购时间窗口内！抢购时间为每天 %02d:%02d - %02d:%02d，当前时间：%02d:%02d",
                    START_TIME.getHour(), START_TIME.getMinute(),
                    END_TIME.getHour(), END_TIME.getMinute(),
                    currentTime.getHour(), currentTime.getMinute()
            );

            LOGGER.warn("抢购时间验证失败：{}", errorMessage);
            throw new RuntimeException(errorMessage);
        }

        LOGGER.info("抢购时间验证通过，当前时间：{}，抢购时间窗口：{} - {}",
                currentTime.format(DateTimeFormatter.ofPattern("HH:mm")),
                START_TIME.format(DateTimeFormatter.ofPattern("HH:mm")),
                END_TIME.format(DateTimeFormatter.ofPattern("HH:mm")));
    }

    @Override
    public Map<String, Object> getTicketCount(String date) {
        Map<String, Object> result = new HashMap<>();

        try {
            LOGGER.info("开始检查票数合法性，日期: {}", date);

            // 基本参数验证
            if (date == null || date.isEmpty()) {
                result.put("valid", false);
                result.put("message", "日期不能为空");
                result.put("code", "INVALID_DATE");
                return result;
            }

            // 解析日期
            LocalDate ticketDate;
            try {
                ticketDate = LocalDate.parse(date);
            } catch (Exception e) {
                result.put("valid", false);
                result.put("message", "日期格式无效，请使用yyyy-MM-dd格式");
                result.put("code", "INVALID_DATE_FORMAT");
                return result;
            }

            // 获取票券实体
            TicketEntity ticketEntity = getTicketEntityByDate(date);
            if (ticketEntity == null) {
                result.put("valid", false);
                result.put("message", "该日期的票券不存在");
                result.put("code", "TICKET_NOT_EXIST");
                result.put("ticketDate", date);
                return result;
            }

            // 检查总票数是否合法
            if (ticketEntity.getTotalCount() <= 0) {
                result.put("valid", false);
                result.put("message", "总票数必须大于0");
                result.put("code", "INVALID_TOTAL_COUNT");
                result.put("ticketDate", date);
                result.put("totalCount", ticketEntity.getTotalCount());
                return result;
            }

            // 检查剩余票数是否合法
            if (ticketEntity.getRemainingCount() < 0) {
                result.put("valid", false);
                result.put("message", "剩余票数不能为负数");
                result.put("code", "INVALID_REMAINING_COUNT");
                result.put("ticketDate", date);
                result.put("totalCount", ticketEntity.getTotalCount());
                result.put("remainingCount", ticketEntity.getRemainingCount());
                return result;
            }

            // 检查剩余票数是否超过总票数
            if (ticketEntity.getRemainingCount() > ticketEntity.getTotalCount()) {
                result.put("valid", false);
                result.put("message", "剩余票数不能超过总票数");
                result.put("code", "REMAINING_EXCEEDS_TOTAL");
                result.put("ticketDate", date);
                result.put("totalCount", ticketEntity.getTotalCount());
                result.put("remainingCount", ticketEntity.getRemainingCount());
                return result;
            }

            // 检查已售票数是否合法
            int soldCount = ticketEntity.getTotalCount() - ticketEntity.getRemainingCount();
            if (soldCount < 0) {
                result.put("valid", false);
                result.put("message", "已售票数不能为负数");
                result.put("code", "INVALID_SOLD_COUNT");
                result.put("ticketDate", date);
                result.put("totalCount", ticketEntity.getTotalCount());
                result.put("remainingCount", ticketEntity.getRemainingCount());
                result.put("soldCount", soldCount);
                return result;
            }

            // 票数合法性检查通过
            result.put("valid", true);
            result.put("message", "票数合法性检查通过");
            result.put("code", "SUCCESS");
            result.put("ticketDate", date);
            result.put("totalCount", ticketEntity.getTotalCount());
            result.put("remainingCount", ticketEntity.getRemainingCount());
            result.put("soldCount", soldCount);
            result.put("soldPercentage", soldCount * 100.0 / ticketEntity.getTotalCount());

            LOGGER.info("票数合法性检查完成，日期: {}, 总票数: {}, 剩余票数: {}, 已售票数: {}",
                    date, ticketEntity.getTotalCount(), ticketEntity.getRemainingCount(), soldCount);

        } catch (Exception e) {
            LOGGER.error("检查票数合法性失败，日期: {}", date, e);
            result.put("valid", false);
            result.put("message", "检查票数合法性时发生错误: " + e.getMessage());
            result.put("code", "ERROR");
            result.put("ticketDate", date);
        }

        return result;
    }

    /**
     * 票券校验，包括票券是否存在、总票数是否合法、剩余票数是否合法、已售票数是否合法
     *
     * @param date
     */
    public void validateTicketCount(String date) {
        validationService.validateTicketCountWithException(date);
    }

    /**
     * 生成票券编号（改进版，确保唯一性）
     * 使用Redis序列号 + 时间戳 + 用户ID + 随机数，确保唯一性
     *
     * @param userId 用户ID
     * @param date   日期
     * @return 唯一票券编码
     */
    private String generateTicketCode(String userId, String date) {
        try {
            // 方案1：使用Redis序列号（推荐）
            String ticketCode = generateTicketCodeWithRedisSequence(userId, date);
            if (ticketCode != null) {
                return ticketCode;
            }

            // 方案2：使用时间戳 + 纳秒（备选）
            return generateTicketCodeWithTimestamp(userId, date);

        } catch (Exception e) {
            LOGGER.warn("Redis序列号生成失败，使用备选方案: {}", e.getMessage());
            // 方案3：使用UUID + 时间戳（兜底）
            return generateTicketCodeWithUUID(userId, date);
        }
    }

    /**
     * 方案1：使用Redis序列号生成票券编码（推荐）
     * 格式：T + 日期 + 序列号 + 用户ID后4位 + 随机数
     */
    private String generateTicketCodeWithRedisSequence(String userId, String date) {
        try {
            String dateStr = LocalDate.parse(date).format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            String userSuffix = String.valueOf(userId).substring(Math.max(0, String.valueOf(userId).length() - 4));

            // 使用Redis INCR生成序列号
            String sequenceKey = "ticket:sequence:" + date;
            Long sequence = stringRedisTemplate.opsForValue().increment(sequenceKey);

            // 设置序列号过期时间（7天后过期）
            stringRedisTemplate.expire(sequenceKey, 7, TimeUnit.DAYS);

            // 生成随机数
            String randomStr = String.valueOf((int) (Math.random() * 1000));

            // 格式：T + 日期 + 序列号(6位) + 用户ID后4位 + 随机数(3位)
            return String.format("T%s%06d%s%03d", dateStr, sequence, userSuffix, Integer.parseInt(randomStr));

        } catch (Exception e) {
            LOGGER.error("Redis序列号生成失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 方案2：使用时间戳 + 纳秒生成票券编码（备选）
     * 格式：T + 日期 + 时间戳 + 用户ID后4位 + 纳秒后3位
     */
    private String generateTicketCodeWithTimestamp(String userId, String date) {
        String dateStr = LocalDate.parse(date).format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String userSuffix = String.valueOf(userId).substring(Math.max(0, String.valueOf(userId).length() - 4));

        // 获取当前时间戳和纳秒
        long timestamp = System.currentTimeMillis();
        long nanoTime = System.nanoTime();

        // 格式：T + 日期 + 时间戳后8位 + 用户ID后4位 + 纳秒后3位
        return String.format("T%s%08d%s%03d", dateStr, timestamp % 100000000, userSuffix, (int) (nanoTime % 1000));
    }

    /**
     * 方案3：使用UUID + 时间戳生成票券编码（兜底）
     * 格式：T + 日期 + UUID前8位 + 用户ID后4位 + 时间戳后3位
     */
    private String generateTicketCodeWithUUID(String userId, String date) {
        String dateStr = LocalDate.parse(date).format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String userSuffix = String.valueOf(userId).substring(Math.max(0, String.valueOf(userId).length() - 4));

        // 生成UUID并取前8位
        String uuidPrefix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);

        // 获取时间戳后3位
        long timestamp = System.currentTimeMillis();
        int timestampSuffix = (int) (timestamp % 1000);

        // 格式：T + 日期 + UUID前8位 + 用户ID后4位 + 时间戳后3位
        return String.format("T%s%s%s%03d", dateStr, uuidPrefix, userSuffix, timestampSuffix);
    }

    /**
     * 验证票券编码唯一性
     *
     * @param ticketCode 票券编码
     * @return 是否唯一
     */
    private boolean isTicketCodeUnique(String ticketCode) {
        try {
            // 检查数据库中是否已存在
            TicketOrder existingOrder = ticketOrderMapper.selectByTicketCode(ticketCode);
            if (existingOrder != null) {
                return false;
            }

            // 检查缓存中是否已存在
            // 这里可以添加缓存检查逻辑

            return true;
        } catch (Exception e) {
            LOGGER.error("验证票券编码唯一性失败: {}", e.getMessage(), e);
            // 验证失败时，为了安全起见，返回false
            return false;
        }
    }

    /**
     * 生成唯一票券编码（带重试机制）
     *
     * @param userId     用户ID
     * @param date       日期
     * @param maxRetries 最大重试次数
     * @return 唯一票券编码
     */
    private String generateUniqueTicketCode(String userId, String date, int maxRetries) {
        for (int i = 0; i < maxRetries; i++) {
            String ticketCode = generateTicketCode(userId, date);

            if (isTicketCodeUnique(ticketCode)) {
                return ticketCode;
            }

            LOGGER.warn("票券编码冲突，重试第{}次: {}", i + 1, ticketCode);

            // 重试前等待一小段时间，避免连续冲突
            try {
                Thread.sleep(10 + (int) (Math.random() * 20));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        // 所有重试都失败，使用时间戳 + 纳秒 + 随机数生成
        LOGGER.error("票券编码生成重试{}次后仍冲突，使用兜底方案", maxRetries);
        return generateTicketCodeWithTimestamp(userId, date) + "_" + System.nanoTime();
    }

    @Override
    public TicketEntity getTicketEntityByDate(String date) {
        try {
            return ticketEntityMapper.selectByDate(date);
        } catch (Exception e) {
            LOGGER.error("根据日期查询票券实体失败: {}", e.getMessage(), e);
            return null;
        }
    }

    @Override
    public TicketEntity getTicketEntityByDateForUpdate(String date) {
        try {
            return ticketEntityMapper.selectByDateForUpdate(date);
        } catch (Exception e) {
            LOGGER.error("根据日期查询票券实体（悲观锁）失败: {}", e.getMessage(), e);
            return null;
        }
    }

    @Override
    public boolean updateTicketStockByOptimistic(TicketEntity ticketEntity) {
        try {
            int result = ticketEntityMapper.updateStockByOptimistic(ticketEntity);
            return result > 0;
        } catch (Exception e) {
            LOGGER.error("乐观锁更新票券库存失败: {}", e.getMessage(), e);
            return false;
        }
    }

    @Override
    public boolean savePurchaseRecord(TicketPurchaseRecord purchaseRecord) {
        try {
            int result = ticketPurchaseRecordMapper.insert(purchaseRecord);
            return result > 0;
        } catch (Exception e) {
            LOGGER.error("保存购买记录失败: {}", e.getMessage(), e);
            return false;
        }
    }

    @Override
    @Transactional
    public void updateDailyTickets() {
        try {
            LOGGER.info("开始执行每日票券更新任务");

            // 计算最近3天的日期范围
            LocalDate today = LocalDate.now();
            String todayStr = today.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            String tomorrowStr = today.plusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            String dayAfterTomorrowStr = today.plusDays(2).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

            // 清空Redis缓存
            ticketCacheManager.deleteTicket(todayStr);
            ticketCacheManager.deleteTicket(tomorrowStr);
            ticketCacheManager.deleteTicket(dayAfterTomorrowStr);

            // 将数据库数据转换为前端模型并放入缓存
            List<TicketEntity> dbTickets = ticketEntityMapper.selectRecentTickets(todayStr, dayAfterTomorrowStr);
            for (TicketEntity dbTicket : dbTickets) {
                Ticket ticket = new Ticket(dbTicket.getDate(), dbTicket.getTotalCount());
                ticket.setRemaining(dbTicket.getRemainingCount());
                ticketCacheManager.saveTicket(dbTicket.getDate(), ticket);
            }

            LOGGER.info("每日票券更新任务执行完成，更新了{}张票券", dbTickets.size());
        } catch (Exception e) {
            LOGGER.error("每日票券更新任务执行失败: {}", e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public Map<String, Object> updateTicketsWithPessimistic(List<UpdateTicketsRequest.TicketUpdate> ticketUpdates) {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> updateResults = new ArrayList<>();
        int successCount = 0;
        int failCount = 0;

        try {
            LOGGER.info("开始使用悲观锁批量修改票数，共{}个更新请求", ticketUpdates.size());

            for (UpdateTicketsRequest.TicketUpdate update : ticketUpdates) {
                Map<String, Object> updateResult = new HashMap<>();
                updateResult.put("date", update.getDate());

                try {
                    // 使用悲观锁查询票券信息
                    TicketEntity ticketEntity = ticketEntityMapper.selectByDateForUpdate(update.getDate());

                    if (ticketEntity == null) {
                        // 如果票券不存在，创建新的票券
                        ticketEntity = new TicketEntity();
                        ticketEntity.setDate(update.getDate());
                        ticketEntity.setName(update.getName() != null ? update.getName() : "票券");
                        ticketEntity.setTotalCount(update.getTotalCount());
                        ticketEntity.setRemainingCount(update.getRemainingCount());
                        ticketEntity.setSoldCount(update.getTotalCount() - update.getRemainingCount());
                        ticketEntity.setVersion(1);
                        ticketEntity.setStatus(1);

                        int insertResult = ticketEntityMapper.insert(ticketEntity);
                        if (insertResult > 0) {
                            updateResult.put("status", "CREATED");
                            updateResult.put("message", "票券创建成功");
                            updateResult.put("oldTotalCount", 0);
                            updateResult.put("newTotalCount", update.getTotalCount());
                            updateResult.put("oldRemainingCount", 0);
                            updateResult.put("newRemainingCount", update.getRemainingCount());
                            successCount++;

                            LOGGER.info("票券创建成功，日期: {}, 总票数: {}, 剩余票数: {}",
                                    update.getDate(), update.getTotalCount(), update.getRemainingCount());
                        } else {
                            updateResult.put("status", "FAILED");
                            updateResult.put("message", "票券创建失败");
                            failCount++;
                        }
                    } else {
                        // 票券存在，使用悲观锁更新
                        int oldTotalCount = ticketEntity.getTotalCount();
                        int oldRemainingCount = ticketEntity.getRemainingCount();
                        int oldSoldCount = ticketEntity.getSoldCount();

                        // 计算新的已售数量
                        int newSoldCount = Math.max(0, update.getTotalCount() - update.getRemainingCount());

                        // 更新票券信息
                        ticketEntity.setTotalCount(update.getTotalCount());
                        ticketEntity.setRemainingCount(update.getRemainingCount());
                        ticketEntity.setSoldCount(newSoldCount);
                        if (update.getName() != null) {
                            ticketEntity.setName(update.getName());
                        }
                        ticketEntity.setVersion(ticketEntity.getVersion() + 1);

                        int updateResultCount = ticketEntityMapper.updateByPrimaryKey(ticketEntity);
                        if (updateResultCount > 0) {
                            updateResult.put("status", "UPDATED");
                            updateResult.put("message", "票券更新成功");
                            updateResult.put("oldTotalCount", oldTotalCount);
                            updateResult.put("newTotalCount", update.getTotalCount());
                            updateResult.put("oldRemainingCount", oldRemainingCount);
                            updateResult.put("newRemainingCount", update.getRemainingCount());
                            successCount++;

                            LOGGER.info("票券更新成功，日期: {}, 总票数: {}->{}, 剩余票数: {}->{}",
                                    update.getDate(), oldTotalCount, update.getTotalCount(),
                                    oldRemainingCount, update.getRemainingCount());
                        } else {
                            updateResult.put("status", "FAILED");
                            updateResult.put("message", "票券更新失败");
                            failCount++;
                        }
                    }

                    // 更新Redis缓存
                    ticketCacheManager.deleteTicket(update.getDate());

                } catch (Exception e) {
                    LOGGER.error("修改票券失败，日期: {}, 错误: {}", update.getDate(), e.getMessage(), e);
                    updateResult.put("status", "ERROR");
                    updateResult.put("message", "修改失败: " + e.getMessage());
                    failCount++;
                }

                updateResults.add(updateResult);
            }

            // 更新票券列表缓存
            try {
                List<Ticket> ticketList = getRecentTickets();
                if (ticketList != null) {
                    ticketCacheManager.saveTicketList(ticketList);
                }
            } catch (Exception e) {
                LOGGER.warn("更新票券列表缓存失败: {}", e.getMessage());
            }

            result.put("status", "SUCCESS");
            result.put("message", String.format("批量修改完成，成功: %d, 失败: %d", successCount, failCount));
            result.put("totalCount", ticketUpdates.size());
            result.put("successCount", successCount);
            result.put("failCount", failCount);
            result.put("updateResults", updateResults);
            result.put("timestamp", System.currentTimeMillis());

            LOGGER.info("批量修改票数完成，成功: {}, 失败: {}", successCount, failCount);

        } catch (Exception e) {
            LOGGER.error("批量修改票数异常: {}", e.getMessage(), e);
            result.put("status", "ERROR");
            result.put("message", "批量修改异常: " + e.getMessage());
            result.put("timestamp", System.currentTimeMillis());
        }

        return result;
    }

    /**
     * 使用悲观锁扣库存购票（包含事务控制）
     *
     * @param request 购票请求
     * @return 购票结果
     * @throws Exception 购票异常
     */
    @Override
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public ApiResponse<PurchaseRecord> purchaseTicketWithPessimisticLock(PurchaseRequest request) throws Exception {
        try {
            LOGGER.info("开始悲观锁购票，用户ID: {}, 日期: {}", request.getUserId(), request.getDate());

            // 参数验证
            if (request == null || request.getUserId() == null || request.getDate() == null) {
                return ApiResponse.error("请求参数不能为空");
            }

            // 验证抢购时间
            validatePurchaseTime(request.getDate());

            // 验证票数
            validateTicketCount(request.getDate());

            // 检查用户是否已购买
            if (hasPurchased(request.getUserId(), request.getDate())) {
                return ApiResponse.error("用户已购买该日期的票券");
            }

            // 使用悲观锁获取票券信息
            TicketEntity ticketEntity = getTicketEntityByDateForUpdate(request.getDate());
            if (ticketEntity == null) {
                return ApiResponse.error("票券不存在");
            }

            // 检查库存
            if (ticketEntity.getRemainingCount() <= 0) {
                return ApiResponse.error("票券已售罄");
            }

            // 扣减库存
            ticketEntity.setRemainingCount(ticketEntity.getRemainingCount() - 1);
            ticketEntity.setSoldCount(ticketEntity.getSoldCount() + 1);
            ticketEntity.setVersion(ticketEntity.getVersion() + 1);

            int updateResult = ticketEntityMapper.updateByPrimaryKey(ticketEntity);
            if (updateResult <= 0) {
                throw new RuntimeException("库存扣减失败");
            }

            // 生成票券编码
            String ticketCode = generateTicketCode(request.getUserId().toString(), request.getDate());

            // 创建购票记录
            TicketPurchaseRecord purchaseRecord = new TicketPurchaseRecord(request.getUserId(), request.getDate(), ticketCode);

            int insertResult = ticketPurchaseRecordMapper.insert(purchaseRecord);
            if (insertResult <= 0) {
                throw new RuntimeException("购票记录创建失败");
            }

            // 更新缓存
            ticketCacheManager.deleteTicket(request.getDate());

            // 构建返回结果
            PurchaseRecord result = new PurchaseRecord(request.getUserId(), LocalDate.parse(request.getDate()), ticketCode);

            LOGGER.info("悲观锁购票成功，用户ID: {}, 日期: {}, 票券编码: {}",
                    request.getUserId(), request.getDate(), ticketCode);

            return ApiResponse.success(result);

        } catch (Exception e) {
            LOGGER.error("悲观锁购票失败，用户ID: {}, 日期: {}, 错误: {}",
                    request.getUserId(), request.getDate(), e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 使用悲观锁购票并生成订单（新方法）
     * 1. 使用SELECT FOR UPDATE锁住票券记录
     * 2. 事务控制
     * 3. 扣减库存
     * 4. 生成ticket_order订单
     *
     * @param request 购票请求
     * @return 购票结果
     * @throws Exception 购票异常
     */
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public ApiResponse<PurchaseRecord> purchaseTicketWithPessimisticLockV2(PurchaseRequest request) throws Exception {
        try {
            LOGGER.info("开始悲观锁购票V2，用户ID: {}, 日期: {}", request.getUserId(), request.getDate());

            // 1. 参数验证
            if (request == null || request.getUserId() == null || request.getDate() == null) {
                return ApiResponse.error("请求参数不能为空");
            }

            Long userId = request.getUserId();
            String purchaseDate = request.getDate();

            // 2. 验证抢购时间
            validatePurchaseTime(purchaseDate);

            // 3. 使用悲观锁查询票券记录（FOR UPDATE）
            TicketEntity ticketEntity = ticketEntityMapper.selectByDateForUpdate(purchaseDate);
            if (ticketEntity == null) {
                return ApiResponse.error("票券不存在");
            }

            // 4. 检查库存
            if (ticketEntity.getRemainingCount() <= 0) {
                return ApiResponse.error("票券已售罄");
            }

            // 5. 在悲观锁保护下检查用户是否已购买（关键修改：将检查移到悲观锁内）
            if (hasPurchased(userId, purchaseDate)) {
                return ApiResponse.error("用户已购买该日期的票券");
            }

            // 6. 扣减库存
            int originalRemaining = ticketEntity.getRemainingCount();
            int originalSold = ticketEntity.getSoldCount();

            ticketEntity.setRemainingCount(originalRemaining - 1);
            ticketEntity.setSoldCount(originalSold + 1);
            ticketEntity.setVersion(ticketEntity.getVersion() + 1);
            ticketEntity.setUpdateTime(new Date());

            int updateResult = ticketEntityMapper.updateByPrimaryKey(ticketEntity);
            if (updateResult <= 0) {
                throw new RuntimeException("库存扣减失败");
            }

            LOGGER.info("库存扣减成功，日期: {}, 原剩余: {}, 现剩余: {}, 原已售: {}, 现已售: {}",
                    purchaseDate, originalRemaining, ticketEntity.getRemainingCount(),
                    originalSold, ticketEntity.getSoldCount());

            // 7. 生成唯一票券编码（使用专业服务）
            String ticketCode = ticketCodeGeneratorService.generateUniqueTicketCode(userId, purchaseDate);

            // 8. 生成订单编号
            String orderNo = generateOrderNo(userId, purchaseDate);

            // 9. 创建ticket_order订单
            TicketOrder ticketOrder = new TicketOrder();
            ticketOrder.setOrderNo(orderNo);
            ticketOrder.setUserId(userId);
            ticketOrder.setTicketId(ticketEntity.getId());
            ticketOrder.setTicketCode(ticketCode);
            ticketOrder.setTicketDate(purchaseDate);
            ticketOrder.setStatus(1); // 待支付
            ticketOrder.setAmount(0L); // 免费票券，金额为0
            ticketOrder.setCreateTime(new Date());
            ticketOrder.setUpdateTime(new Date());
            ticketOrder.setRemark("悲观锁购票生成");

            int insertResult = ticketOrderMapper.insert(ticketOrder);
            if (insertResult <= 0) {
                throw new RuntimeException("订单创建失败");
            }

            LOGGER.info("订单创建成功，订单号: {}, 用户ID: {}, 票券编码: {}",
                    orderNo, userId, ticketCode);

            // 10. 更新缓存
            ticketCacheManager.deleteTicket(purchaseDate);

            // 添加购买记录到缓存
            PurchaseRecord purchaseRecord = new PurchaseRecord(userId, LocalDate.parse(purchaseDate), ticketCode);
            ticketCacheManager.addPurchaseRecord(userId, purchaseDate, purchaseRecord);

            // 11. 构建返回结果
            PurchaseRecord result = new PurchaseRecord(userId, LocalDate.parse(purchaseDate), ticketCode);

            LOGGER.info("悲观锁购票V2成功，用户ID: {}, 日期: {}, 票券编码: {}, 订单号: {}",
                    userId, purchaseDate, ticketCode, orderNo);

            return ApiResponse.success(result);

        } catch (Exception e) {
            // 检查是否是数据库唯一约束冲突（用户重复购买）
            if (e.getMessage() != null && e.getMessage().contains("Duplicate entry")) {
                LOGGER.warn("用户重复购买，用户ID: {}, 日期: {}, 错误: {}", 
                        request.getUserId(), request.getDate(), e.getMessage());
                return ApiResponse.error("用户已购买该日期的票券");
            }
            
            LOGGER.error("悲观锁购票V2失败，用户ID: {}, 日期: {}, 错误: {}",
                    request.getUserId(), request.getDate(), e.getMessage(), e);
            throw e;
        }
    }

    // 悲观锁购票,仅处理购票，不做校验
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public PurchaseRecord doPurchaseTicketWithPessimisticLockV2(PurchaseRequest request) throws Exception {

        try {
            LOGGER.info("开始悲观锁购票V2，用户ID: {}, 日期: {}", request.getUserId(), request.getDate());

            Long userId = request.getUserId();
            String purchaseDate = request.getDate();

            // 3. 检查用户是否已购买
            if (hasPurchased(userId, purchaseDate)) {
                throw new BusinessException("您已购买过当天的票券，每人每天限购一张");
            }

            // 4. 使用悲观锁查询票券记录（FOR UPDATE）
            TicketEntity ticketEntity = ticketEntityMapper.selectByDateForUpdate(purchaseDate);
            if (ticketEntity == null) {
                throw new BusinessException("票券不存在");
            }

            // 5. 检查库存
            if (ticketEntity.getRemainingCount() <= 0) {
                throw new BusinessException("票券已售罄");
            }

            // 6. 扣减库存
            int originalRemaining = ticketEntity.getRemainingCount();
            int originalSold = ticketEntity.getSoldCount();

            ticketEntity.setRemainingCount(originalRemaining - 1);
            ticketEntity.setSoldCount(originalSold + 1);
            ticketEntity.setVersion(ticketEntity.getVersion() + 1);
            ticketEntity.setUpdateTime(new Date());

            int updateResult = ticketEntityMapper.updateByPrimaryKey(ticketEntity);
            if (updateResult <= 0) {
                throw new RuntimeException("库存扣减失败");
            }

            // 10. 删除缓存
            ticketCacheManager.deleteTicket(purchaseDate);

            LOGGER.info("库存扣减成功，日期: {}, 原剩余: {}, 现剩余: {}, 原已售: {}, 现已售: {}",
                    purchaseDate, originalRemaining, ticketEntity.getRemainingCount(),
                    originalSold, ticketEntity.getSoldCount());

            // 7. 生成唯一票券编码（使用专业服务）
            String ticketCode = ticketCodeGeneratorService.generateUniqueTicketCode(userId, purchaseDate);

            // 8. 生成订单编号
            String orderNo = generateOrderNo(userId, purchaseDate);

            // 9. 创建ticket_order订单
            TicketOrder ticketOrder = new TicketOrder();
            ticketOrder.setOrderNo(orderNo);
            ticketOrder.setUserId(userId);
            ticketOrder.setTicketId(ticketEntity.getId());
            ticketOrder.setTicketCode(ticketCode);
            ticketOrder.setTicketDate(purchaseDate);
            ticketOrder.setStatus(1); // 待支付
            ticketOrder.setAmount(0L); // 免费票券，金额为0
            ticketOrder.setCreateTime(new Date());
            ticketOrder.setUpdateTime(new Date());
            ticketOrder.setRemark("悲观锁购票生成");

            int insertResult = ticketOrderMapper.insert(ticketOrder);
            if (insertResult <= 0) {
                throw new RuntimeException("订单创建失败");
            }

            LOGGER.info("订单创建成功，订单号: {}, 用户ID: {}, 票券编码: {}",
                    orderNo, userId, ticketCode);

            // 添加购买记录到缓存
            PurchaseRecord purchaseRecord = new PurchaseRecord(userId, LocalDate.parse(purchaseDate), ticketCode);
            ticketCacheManager.addPurchaseRecord(userId, purchaseDate, purchaseRecord);

            // 11. 构建返回结果
            PurchaseRecord result = new PurchaseRecord(userId, LocalDate.parse(purchaseDate), ticketCode);

            LOGGER.info("悲观锁购票V2成功，用户ID: {}, 日期: {}, 票券编码: {}, 订单号: {}",
                    userId, purchaseDate, ticketCode, orderNo);

            return result;

        } catch (Exception e) {
            LOGGER.error("悲观锁购票V2失败，用户ID: {}, 日期: {}, 错误: {}",
                    request.getUserId(), request.getDate(), e.getMessage(), e);
            throw e;
        }
    }


    /**
     * 乐观锁购票
     * <p>
     * 1. 优先选乐观锁的场景
     * 高并发、低冲突：如电商商品库存更新、秒杀业务、用户积分修改（大部分时间无冲突，偶尔冲突可通过重试解决）；
     * 读多写少：如新闻资讯阅读、商品详情查询（读操作无需锁，写操作少，冲突概率低）；
     * 分布式系统：跨数据库、跨服务的并发操作（悲观锁无法跨节点锁定，乐观锁通过版本号可实现分布式校验）。
     * 2. 优先选悲观锁的场景
     * 低并发、高冲突：如金融交易（转账、支付），冲突概率高，需确保操作原子性，避免重试导致的重复交易；
     * 写多读少：如订单状态更新（大量事务同时修改订单状态，冲突频繁，悲观锁可避免重试开销）；
     * 数据一致性要求极高：如库存不允许超卖、余额不允许负数（悲观锁锁定后操作，可 100% 避免并发问题，无需担心重试漏判）。
     */
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public PurchaseRecord doPurchaseTicketWithOptimisticLock(PurchaseRequest request) throws Exception {
        try {
            LOGGER.info("开始乐观锁购票购票，用户ID: {}, 日期: {}", request.getUserId(), request.getDate());

            // 检查是否已经购买（从数据库查询）
            if (hasPurchased(request.getUserId(), request.getDate())) {
                throw new IllegalStateException("您已购买过当天的票券，每人每天限购一张");
            }

            Long userId = request.getUserId();
            String purchaseDate = request.getDate();

            // 4. 使用乐观锁扣减库存，支持重试机制
            TicketEntity ticketEntity = null;
            int maxRetries = 3;
            int retryCount = 0;
            boolean stockUpdated = false;
            int originalRemaining = 0;
            int originalSold = 0;

            while (retryCount < maxRetries && !stockUpdated) {
                try {
                    // 查询票券信息（不使用FOR UPDATE）
                    ticketEntity = ticketEntityMapper.selectByDate(purchaseDate);
                    if (ticketEntity == null) {
                        throw new BusinessException("票券不存在");
                    }

                    // 检查库存
                    if (ticketEntity.getRemainingCount() <= 0) {
                        throw new BusinessException("票券已售罄");
                    }

                    // 准备扣减库存（乐观锁）
                    originalRemaining = ticketEntity.getRemainingCount();
                    originalSold = ticketEntity.getSoldCount();
                    int originalVersion = ticketEntity.getVersion();

                    ticketEntity.setRemainingCount(originalRemaining - 1);
                    ticketEntity.setSoldCount(originalSold + 1);
                    ticketEntity.setUpdateTime(new Date());

                    // 使用乐观锁更新库存（version字段自动处理）
                    int updateResult = ticketEntityMapper.updateStockByOptimistic(ticketEntity);
                    if (updateResult > 0) {
                        stockUpdated = true;
                        LOGGER.info("乐观锁库存扣减成功，日期: {}, 原剩余: {}, 现剩余: {}, 原已售: {}, 现已售: {}, 版本: {}->{}",
                                purchaseDate, originalRemaining, ticketEntity.getRemainingCount(),
                                originalSold, ticketEntity.getSoldCount(), originalVersion, ticketEntity.getVersion());
                    } else {
                        // 乐观锁更新失败，版本冲突
                        retryCount++;
                        if (retryCount < maxRetries) {
                            LOGGER.warn("乐观锁更新失败，版本冲突，重试第{}次，用户ID: {}, 日期: {}",
                                    retryCount, userId, purchaseDate);
                            // 短暂等待后重试
                            Thread.sleep(10 + (int) (Math.random() * 20));
                        } else {
                            throw new RuntimeException("乐观锁更新失败，重试" + maxRetries + "次后仍失败");
                        }
                    }
                } catch (Exception e) {
                    if (e instanceof BusinessException) {
                        throw e; // 业务异常直接抛出
                    }
                    retryCount++;
                    if (retryCount >= maxRetries) {
                        throw new RuntimeException("乐观锁购票失败，重试" + maxRetries + "次后仍失败: " + e.getMessage(), e);
                    }
                    LOGGER.warn("乐观锁购票异常，重试第{}次，用户ID: {}, 日期: {}, 错误: {}",
                            retryCount, userId, purchaseDate, e.getMessage());
                    Thread.sleep(10 + (int) (Math.random() * 20));
                }
            }

            if (!stockUpdated) {
                throw new RuntimeException("乐观锁库存扣减失败，重试" + maxRetries + "次后仍失败");
            }

            // 10. 删除缓存
            ticketCacheManager.deleteTicket(purchaseDate);

            LOGGER.info("库存扣减成功，日期: {}, 原剩余: {}, 现剩余: {}, 原已售: {}, 现已售: {}",
                    purchaseDate, originalRemaining, ticketEntity.getRemainingCount(),
                    originalSold, ticketEntity.getSoldCount());

            // 7. 生成唯一票券编码（使用专业服务）
            String ticketCode = ticketCodeGeneratorService.generateUniqueTicketCode(userId, purchaseDate);

            // 8. 生成订单编号
            String orderNo = generateOrderNo(userId, purchaseDate);

            // 9. 创建ticket_order订单
            TicketOrder ticketOrder = new TicketOrder();
            ticketOrder.setOrderNo(orderNo);
            ticketOrder.setUserId(userId);
            ticketOrder.setTicketId(ticketEntity.getId());
            ticketOrder.setTicketCode(ticketCode);
            ticketOrder.setTicketDate(purchaseDate);
            ticketOrder.setStatus(1); // 待支付
            ticketOrder.setAmount(0L); // 免费票券，金额为0
            ticketOrder.setCreateTime(new Date());
            ticketOrder.setUpdateTime(new Date());
            ticketOrder.setRemark("乐观锁购票生成");

            int insertResult = ticketOrderMapper.insert(ticketOrder);
            if (insertResult <= 0) {
                throw new RuntimeException("订单创建失败");
            }

            LOGGER.info("订单创建成功，订单号: {}, 用户ID: {}, 票券编码: {}",
                    orderNo, userId, ticketCode);

            // 添加购买记录到缓存
            PurchaseRecord purchaseRecord = new PurchaseRecord(userId, LocalDate.parse(purchaseDate), ticketCode);
            ticketCacheManager.addPurchaseRecord(userId, purchaseDate, purchaseRecord);

            // 11. 构建返回结果
            PurchaseRecord result = new PurchaseRecord(userId, LocalDate.parse(purchaseDate), ticketCode);

            LOGGER.info("乐观锁购票成功，用户ID: {}, 日期: {}, 票券编码: {}, 订单号: {}",
                    userId, purchaseDate, ticketCode, orderNo);

            return result;

        } catch (Exception e) {
            LOGGER.error("乐观锁购票失败，用户ID: {}, 日期: {}, 错误: {}",
                    request.getUserId(), request.getDate(), e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 异步乐观锁购票，不需要限流检查
     * @param request
     * @return
     * @throws Exception
     */
    @Override
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public PurchaseRecord asyncPurchaseTicketWithOptimisticLock(PurchaseRequest request) throws Exception {
        // *********入参为空校验********
        validNullParam(request);

        // *********合法性校验：抢购时间内、用户登录、token、是否重复抢购、黑名单等********
        validLegalParam(request);

        PurchaseRecord purchaseRecord = doPurchaseTicketWithOptimisticLock(request);

        return purchaseRecord;
    }


    /**
     * 生成订单编号
     * 格式：TB + 时间戳 + 用户ID后4位 + 随机数
     *
     * @param userId 用户ID
     * @param date   购票日期
     * @return 订单编号
     */
    private String generateOrderNo(Long userId, String date) {
        long timestamp = System.currentTimeMillis();
        String userIdSuffix = String.valueOf(userId).substring(Math.max(0, String.valueOf(userId).length() - 4));
        int random = (int) (Math.random() * 1000);

        return String.format("TB%d%s%03d", timestamp, userIdSuffix, random);
    }

    @Override
    public List<Ticket> getRecentTicketsWithUserStatus(Long userId) {
        try {
            // 获取基础票券信息
            List<Ticket> tickets = getRecentTickets();

            // 如果用户未登录，直接返回基础信息
            if (userId == null) {
                LOGGER.info("用户未登录，返回基础票券信息");
                return tickets;
            }

            // 为每个票券添加用户购买状态
            for (Ticket ticket : tickets) {
                boolean hasPurchased = checkUserPurchaseStatus(userId, ticket.getDate());
                ticket.setUserPurchased(hasPurchased);
                LOGGER.debug("票券日期: {}, 用户ID: {}, 已购买: {}", ticket.getDate(), userId, hasPurchased);
            }

            LOGGER.info("获取票券信息成功，用户ID: {}, 票券数量: {}", userId, tickets.size());
            return tickets;

        } catch (Exception e) {
            LOGGER.error("获取带用户状态的票券信息失败，用户ID: {}", userId, e);
            // 如果出错，返回基础票券信息
            return getRecentTickets();
        }
    }

    /**
     * 检查用户是否已购买指定日期的票券
     * 优先从缓存查询，缓存未命中则从数据库ticket_order表查询
     *
     * @param userId 用户ID
     * @param date   日期
     * @return 是否已购买
     */
    private boolean checkUserPurchaseStatus(Long userId, String date) {
        try {
            if (userId == null) {
                return false;
            }

            // 1. 先从缓存查询用户购买状态
            boolean fromCache = checkUserPurchaseStatusFromCache(userId, date);
            if (fromCache) {
                LOGGER.debug("从缓存查询到用户购买状态，用户ID: {}, 日期: {}, 已购买: true", userId, date);
                return true;
            }

            // 2. 缓存中没有，查询数据库ticket_order表
            LOGGER.debug("缓存中未找到用户购买记录，尝试从数据库查询，用户ID: {}, 日期: {}", userId, date);
            TicketOrder ticketOrder = ticketOrderMapper.selectByUserIdAndDate(userId, date);

            boolean hasPurchased = ticketOrder != null;

            // 3. 将查询结果写入缓存
            String cacheKey = CacheKey.USER_HAS_ORDER.getKey() + "_" + date + "_" + userId;
            if (hasPurchased) {
                stringRedisTemplate.opsForValue().set(cacheKey, "1", 12, TimeUnit.HOURS);
                LOGGER.debug("用户已购买，写入缓存，用户ID: {}, 日期: {}, 缓存键: {}", userId, date, cacheKey);
            } else {
                stringRedisTemplate.opsForValue().set(cacheKey, "0", 12, TimeUnit.HOURS);
                LOGGER.debug("用户未购买，写入缓存，用户ID: {}, 日期: {}, 缓存键: {}", userId, date, cacheKey);
            }

            LOGGER.debug("从数据库查询用户购买状态，用户ID: {}, 日期: {}, 已购买: {}", userId, date, hasPurchased);
            return hasPurchased;

        } catch (Exception e) {
            LOGGER.error("查询用户购买状态失败，用户ID: {}, 日期: {}", userId, date, e);
            return false;
        }
    }

    /**
     * 从缓存查询用户购买状态
     *
     * @param userId 用户ID
     * @param date   日期
     * @return 是否已购买
     */
    private boolean checkUserPurchaseStatusFromCache(Long userId, String date) {
        try {
            if (userId == null) {
                return false;
            }

            // 从Redis缓存查询
            String cacheKey = CacheKey.USER_HAS_ORDER.getKey() + "_" + date + "_" + userId;
            String cacheValue = stringRedisTemplate.opsForValue().get(cacheKey);

            if (cacheValue != null) {
                boolean hasPurchased = "1".equals(cacheValue);
                LOGGER.debug("从缓存查询用户购买状态，用户ID: {}, 日期: {}, 已购买: {}", userId, date, hasPurchased);
                return hasPurchased;
            }

            return false;

        } catch (Exception e) {
            LOGGER.error("从缓存查询用户购买状态失败，用户ID: {}, 日期: {}", userId, date, e);
            return false;
        }
    }

    /**
     * 验证取消条件
     *
     * @param order   订单信息
     * @param request 取消购票请求
     */
    private void validateCancelConditions(TicketOrder order, CancelPurchaseRequest request) {
        // 1. 权限验证：只能取消自己的订单
        if (!order.getUserId().equals(request.getUserId())) {
            throw new IllegalStateException("只能取消自己的订单");
        }

        // 2. 状态验证：只能取消待支付或已支付的订单
        if (order.getStatus() != 1 && order.getStatus() != 2) {
            throw new IllegalStateException("只能取消待支付或已支付的订单");
        }

        // 3. 时间验证：只能在购票后24小时内取消
        Date createTime = order.getCreateTime();
        Date now = new Date();
        long timeDiff = now.getTime() - createTime.getTime();
        long hoursDiff = timeDiff / (1000 * 60 * 60);

        if (hoursDiff > 24) {
            throw new IllegalStateException("购票超过24小时，无法取消");
        }

        // 4. 活动状态验证：秒杀活动暂停时不允许取消
        try {
            if (miaoshaStatusService.isMiaoshaPaused()) {
                throw new IllegalStateException("秒杀活动正在维护中，无法取消购票");
            }
        } catch (Exception e) {
            LOGGER.warn("检查秒杀活动状态失败，继续执行: {}", e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CancelPurchaseResponse cancelPurchase(CancelPurchaseRequest request) throws Exception {
        try {
            LOGGER.info("开始处理取消购票请求，用户ID: {}, 订单号: {}, 票券编码: {}",
                    request.getUserId(), request.getOrderNo(), request.getTicketCode());

            // 1. 参数验证
            validateCancelRequest(request);

            // 2. 查询订单信息
            TicketOrder order = getOrderByRequest(request);
            if (order == null) {
                throw new IllegalArgumentException("订单不存在");
            }

            // 3. 验证取消条件
            validateCancelConditions(order, request);

            // 4. 执行取消操作
            CancelPurchaseResponse response = executeCancel(order, request);

            // 5. 更新缓存
            updateCacheAfterCancel(order);

            LOGGER.info("取消购票成功，用户ID: {}, 订单号: {}, 票券编码: {}",
                    order.getUserId(), order.getOrderNo(), order.getTicketCode());

            return response;

        } catch (Exception e) {
            LOGGER.error("取消购票失败，用户ID: {}, 订单号: {}, 票券编码: {}",
                    request.getUserId(), request.getOrderNo(), request.getTicketCode(), e);
            throw e;
        }
    }

    /**
     * 验证取消购票请求参数
     *
     * @param request 取消购票请求
     */
    private void validateCancelRequest(CancelPurchaseRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求参数不能为空");
        }

        if (request.getUserId() == null) {
            throw new IllegalArgumentException("用户ID不能为空");
        }

        // 至少需要提供一种查询方式
        if (request.getOrderNo() == null && request.getTicketCode() == null && request.getDate() == null) {
            throw new IllegalArgumentException("至少需要提供订单号、票券编码或日期中的一种");
        }

//        if (request.getCancelReason() == null || request.getCancelReason().trim().isEmpty()) {
//            throw new IllegalArgumentException("取消原因不能为空");
//        }

//        if (request.getVerifyHash() == null || request.getVerifyHash().trim().isEmpty()) {
//            throw new IllegalArgumentException("验证哈希不能为空");
//        }
    }

    /**
     * 根据请求参数查询订单信息
     *
     * @param request 取消购票请求
     * @return 订单信息
     */
    private TicketOrder getOrderByRequest(CancelPurchaseRequest request) {
        // 优先通过订单号查询
        if (request.getOrderNo() != null) {
            return ticketOrderMapper.selectByOrderNo(request.getOrderNo());
        }

        // 其次通过票券编码查询
        if (request.getTicketCode() != null) {
            return ticketOrderMapper.selectByTicketCode(request.getTicketCode());
        }

        // 最后通过用户ID+日期查询
        if (request.getUserId() != null && request.getDate() != null) {
            return ticketOrderMapper.selectByUserIdAndDate(request.getUserId(), request.getDate());
        }

        throw new IllegalArgumentException("缺少必要的查询参数");
    }

    /**
     * 执行取消操作
     *
     * @param order   订单信息
     * @param request 取消购票请求
     * @return 取消购票响应
     */
    private CancelPurchaseResponse executeCancel(TicketOrder order, CancelPurchaseRequest request) {
        // 1. 恢复票券库存
        restoreTicketStock(order);

        // 2. 更新订单状态
        updateOrderStatus(order, request.getCancelReason());

        // 3. 构建响应
        String cancelTime = new Date().toString();
        Integer refundAmount = order.getAmount() != null ? order.getAmount().intValue() : 0;

        return new CancelPurchaseResponse(
                order.getOrderNo(),
                order.getTicketCode(),
                cancelTime,
                request.getCancelReason(),
                refundAmount,
                "已取消"
        );
    }

    /**
     * 恢复票券库存
     *
     * @param order 订单信息
     */
    private void restoreTicketStock(TicketOrder order) {
        // 1. 使用悲观锁查询票券信息
        TicketEntity ticketEntity = ticketEntityMapper.selectByDateForUpdate(order.getTicketDate());
        if (ticketEntity == null) {
            throw new RuntimeException("票券不存在，无法恢复库存");
        }

        // 2. 恢复库存
        ticketEntity.setRemainingCount(ticketEntity.getRemainingCount() + 1);
        ticketEntity.setSoldCount(ticketEntity.getSoldCount() - 1);
        ticketEntity.setVersion(ticketEntity.getVersion() + 1);
        ticketEntity.setUpdateTime(new Date());

        // 3. 更新数据库 TODO 乐观锁，version不需要在业务层加1
        int result = ticketEntityMapper.updateByPrimaryKey(ticketEntity);
        if (result <= 0) {
            throw new RuntimeException("库存恢复失败");
        }

        LOGGER.info("库存恢复成功，日期: {}, 原剩余: {}, 现剩余: {}, 原已售: {}, 现已售: {}",
                order.getTicketDate(), ticketEntity.getRemainingCount() - 1,
                ticketEntity.getRemainingCount(), ticketEntity.getSoldCount() + 1,
                ticketEntity.getSoldCount());
    }

    /**
     * 更新订单状态
     *
     * @param order        订单信息
     * @param cancelReason 取消原因
     */
    private void updateOrderStatus(TicketOrder order, String cancelReason) {
        order.setStatus(3); // 已取消
        order.setUpdateTime(new Date());
        order.setRemark("用户取消：" + cancelReason);

        int result = ticketOrderMapper.updateByPrimaryKey(order);
        if (result <= 0) {
            throw new RuntimeException("订单状态更新失败");
        }

        LOGGER.info("订单状态更新成功，订单号: {}, 新状态: 已取消", order.getOrderNo());
    }

    /**
     * 取消购票后更新缓存
     *
     * @param order 订单信息
     */
    private void updateCacheAfterCancel(TicketOrder order) {
        try {
            // 1. 清除用户购买状态缓存
            String cacheKey = CacheKey.USER_HAS_ORDER.getKey() + "_" + order.getTicketDate() + "_" + order.getUserId();
            stringRedisTemplate.delete(cacheKey);

            // 2. 清除购买记录缓存
            ticketCacheManager.deletePurchaseRecord(order.getUserId(), order.getTicketDate());

            // 3. 清除票券库存缓存
            ticketCacheManager.deleteTicket(order.getTicketDate());

            // 清除购票状态 CacheKey.USER_HAS_ORDER.getKey() + "_" + date + "_" + userId
            ticketCacheManager.clearUserPurchaseStatus(order.getUserId(), order.getTicketDate());

            // 4. 清除票券列表缓存
//            ticketCacheManager.deleteTicketList();

            LOGGER.debug("缓存更新完成，订单号: {}, 票券日期: {}", order.getOrderNo(), order.getTicketDate());

        } catch (Exception e) {
            LOGGER.warn("缓存更新失败，订单号: {}, 票券日期: {}", order.getOrderNo(), order.getTicketDate(), e);
            // 缓存更新失败不影响主流程
        }
    }

    @Override
    public ApiResponse<List<TicketOrder>> getOrdersByUserId(Long userId) {
        if (userId == null) {
            return ApiResponse.error("用户ID不能为空");
        }
        List<TicketOrder> orders = ticketOrderMapper.selectByUserId(userId);
        return ApiResponse.success(orders);
    }

    @Override
    public ApiResponse<TicketOrder> getOrderById(Long orderId) {
        if (orderId == null) {
            return ApiResponse.error("订单ID不能为空");
        }
        TicketOrder order = ticketOrderMapper.selectById(orderId);
        if (order == null) {
            return ApiResponse.error("订单不存在");
        }
        return ApiResponse.success(order);
    }

    // Add at the end of the class
    public void clearUserPurchaseStatus(Long userId, String date) {
        String key = CacheKey.USER_HAS_ORDER.getKey() + "_" + date + "_" + userId;
        stringRedisTemplate.delete(key);
        LOGGER.info("Cleared user purchase status cache for userId: {}, date: {}", userId, date);
    }
}