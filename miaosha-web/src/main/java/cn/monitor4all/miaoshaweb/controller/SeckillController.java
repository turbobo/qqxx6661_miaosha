package cn.monitor4all.miaoshaweb.controller;

import cn.monitor4all.miaoshadao.model.ApiResponse;
import cn.monitor4all.miaoshaservice.service.CircuitBreakerService;
import cn.monitor4all.miaoshaservice.service.DistributedLockService;
import cn.monitor4all.miaoshaservice.service.DistributedTokenBucketService;
import cn.monitor4all.miaoshaservice.service.IdempotencyService;
import cn.monitor4all.miaoshaservice.service.OrderService;
import cn.monitor4all.miaoshaservice.service.StockService;
import cn.monitor4all.miaoshaservice.service.UserService;
import com.alibaba.fastjson.JSONObject;
import com.google.common.util.concurrent.RateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 秒杀系统演进全方案模拟控制器（阶段 0 ~ 阶段 6）。
 * <p>
 * 本控制器按《从零开始打造简易秒杀系统》系列的演进脉络，将每一代方案实现为可独立调用的接口，
 * 用于验证"痛点 → 方案 → 新问题"的演进规律。所有接口复用 Service 层既有能力，不重复造轮子。
 * </p>
 *
 * <pre>
 * 正确性线：无锁(超卖) → 乐观锁 → 悲观锁 → 接口验签 → 幂等
 * 性能线  ：单机DB    → 令牌桶限流 → Redis缓存 → MQ异步 → 分布式增强
 *
 * 阶段 0  /seckill/stage0/wrong-order/{sid}                无锁下单（反面教材，必超卖）
 * 阶段 1  /seckill/stage1/optimistic-order/{sid}           乐观锁 version CAS 防超卖
 * 阶段 2  /seckill/stage2/optimistic-order-limited/{sid}   Guava 令牌桶限流 + 乐观锁
 *         /seckill/stage2/pessimistic-order/{sid}          SELECT FOR UPDATE 悲观锁
 * 阶段 3  /seckill/stage3/verify-hash                      接口隐藏：获取一次性验签 hash
 *         /seckill/stage3/verified-order                   携带 verifyHash 下单
 *         /seckill/stage3/verified-order-limited           verifyHash + 单用户限频
 * 阶段 4  /seckill/stage4/stock-by-db/{sid}                对照实验：DB 查库存
 *         /seckill/stage4/stock-by-cache/{sid}             对照实验：缓存查库存
 *         /seckill/stage4/cache-v1/{sid}                   先删缓存 → 扣库
 *         /seckill/stage4/cache-v2/{sid}                   扣库 → 删缓存
 *         /seckill/stage4/cache-v3/{sid}                   V1 + 延迟双删
 *         /seckill/stage4/cache-v4/{sid}                   V2 + 延迟双删 + MQ 兜底删缓存
 *         /seckill/stage4/cache-v5                         "全都要"组合（反面教材，性能崩塌）
 * 阶段 5  /seckill/stage5/order-with-mq                    MQ 异步削峰下单（不碰 DB）
 *         /seckill/stage5/purchase-result                  前端轮询订单处理结果
 * 阶段 6  /seckill/stage6/locked-order/{sid}               Redisson 分布式锁 + 乐观锁
 *         /seckill/stage6/idempotent-order                 幂等下单（requestId + SETNX + 用户已购检查）
 *         /seckill/stage6/token-bucket-order/{sid}         Redis + Lua 分布式令牌桶限流
 *         /seckill/stage6/order-with-circuit-breaker/{sid} 受 DB 熔断保护的下单（含降级）
 *         /seckill/stage6/circuit-breaker-status           查询熔断器状态
 * </pre>
 * <p>
 * 实测数据索引：jmeter/reports/REPORT.md（40 轮压测）、docs/guides/系统演进方案-qoder分析.md（逐版本拆解）。
 * 测试前置：Redis、MySQL（stock / stock_order / user 表，见 sql/miaosha.sql）、RabbitMQ（阶段 4/5 需要）。
 * 注意：userId 需存在于 user 表（默认仅 id=1）；接口验签仅在每天 9:00-23:00 的抢购时段内可成功。
 * </p>
 *
 * @author system
 * @date 2026/09/17
 */
@RestController
@RequestMapping("/seckill")
@CrossOrigin
public class SeckillController {

    private static final Logger LOGGER = LoggerFactory.getLogger(SeckillController.class);

    /** 订单队列（与 OrderMqReceiver 消费的队列保持一致） */
    private static final String ORDER_QUEUE = "orderQueue";

    /** 删缓存补偿队列（与 DelCacheReceiver 消费的队列保持一致） */
    private static final String DEL_CACHE_QUEUE = "delCache";

    /** Guava 令牌桶：每秒放行 10 个请求（阶段 2，单机版） */
    private static final int GUAVA_RATE_LIMIT_PERMITS = 10;

    /** Guava 令牌桶非阻塞获取令牌的最长等待时间（毫秒） */
    private static final long GUAVA_RATE_LIMIT_TIMEOUT_MS = 1000;

    /** 延迟双删的延迟时间（毫秒），取"读请求耗时 + 几百毫秒"的经验值 */
    private static final long DELAY_MILLISECONDS = 1000L;

    /** 分布式令牌桶：桶容量（阶段 6） */
    private static final int TOKEN_BUCKET_CAPACITY = 10;

    /** 分布式令牌桶：令牌填充速率（个/秒） */
    private static final double TOKEN_BUCKET_RATE = 10.0D;

    /** 分布式令牌桶限流键前缀 */
    private static final String TOKEN_BUCKET_KEY_PREFIX = "seckill:token_bucket:";

    /** 分布式锁键前缀（按商品维度加锁） */
    private static final String LOCK_KEY_PREFIX = "seckill:lock:stock:";

    /** 获取分布式锁的最大等待时间（秒） */
    private static final long LOCK_WAIT_SECONDS = 3L;

    /** 分布式锁的持有时长（秒），Watchdog 自动续期兜底 */
    private static final long LOCK_LEASE_SECONDS = 10L;

    /**
     * 延迟双删线程池。
     * 按规范使用有界队列 + 命名线程，拒绝策略为 Abort（拒绝时由调用方改走 MQ 兜底删除）。
     */
    private static final ExecutorService DELAY_DELETE_EXECUTOR = new ThreadPoolExecutor(
            2, 4, 60L, TimeUnit.SECONDS,
            new ArrayBlockingQueue<Runnable>(500),
            new NamedThreadFactory("seckill-delay-delete-"),
            new ThreadPoolExecutor.AbortPolicy());

    @Resource
    private OrderService orderService;

    @Resource
    private StockService stockService;

    @Resource
    private UserService userService;

    @Resource
    private AmqpTemplate rabbitTemplate;

    @Resource
    private DistributedLockService distributedLockService;

    @Resource
    private IdempotencyService idempotencyService;

    @Resource
    private CircuitBreakerService circuitBreakerService;

    @Resource
    private DistributedTokenBucketService distributedTokenBucketService;

    /** Guava 令牌桶实例（线程安全，阶段 2 使用） */
    private final RateLimiter guavaRateLimiter = RateLimiter.create(GUAVA_RATE_LIMIT_PERMITS);

    // ==================================================================
    // 阶段 0：无锁基线（反面教材）
    // ==================================================================

    /**
     * 阶段 0：无锁下单（反面教材）。
     * <p>
     * 问题：查库存 → sale+1 → 插订单三步无原子性，高并发下扣减相互覆盖。
     * 实测：50 VUs 下 HTTP 成功率 100%，业务成功率仅 5.6%（15741 次请求仅落库 888 单）。
     *
     * @param sid 商品ID
     * @return 订单ID 或失败原因
     */
    @GetMapping("/stage0/wrong-order/{sid}")
    public String createWrongOrder(@PathVariable int sid) {
        try {
            int id = orderService.createWrongOrder(sid);
            LOGGER.info("阶段0：无锁下单，创建订单id=[{}]", id);
            return "创建订单id：" + id;
        } catch (Exception e) {
            LOGGER.error("阶段0：无锁下单异常：[{}]", e.getMessage());
            return "购买失败：" + e.getMessage();
        }
    }

    // ==================================================================
    // 阶段 1：乐观锁防超卖
    // ==================================================================

    /**
     * 阶段 1：乐观锁下单（version CAS 防超卖）。
     * <p>
     * 方案：UPDATE ... SET sale=sale+1, version=version+1 WHERE id=? AND version=?。
     * 实测：可防住超卖，但高争用下 CAS 冲突率超 97%，业务成功率仅 1.1%~1.7%。
     *
     * @param sid 商品ID
     * @return 剩余库存 或失败原因
     */
    @GetMapping("/stage1/optimistic-order/{sid}")
    public String createOptimisticOrder(@PathVariable int sid) {
        try {
            int stockLeft = orderService.createOptimisticOrder(sid);
            LOGGER.info("阶段1：乐观锁下单成功，剩余库存=[{}]", stockLeft);
            return String.format("购买成功，剩余库存为：%d", stockLeft);
        } catch (Exception e) {
            LOGGER.error("阶段1：乐观锁下单失败：[{}]", e.getMessage());
            return "购买失败：" + e.getMessage();
        }
    }

    // ==================================================================
    // 阶段 2：令牌桶限流 + 悲观锁补充
    // ==================================================================

    /**
     * 阶段 2：Guava 令牌桶限流 + 乐观锁下单。
     * <p>
     * 方案：每秒放行 10 个请求（非阻塞 tryAcquire，1 秒拿不到令牌直接失败），
     * 把突发流量削成均匀流量，显著降低乐观锁 CAS 冲突。
     * 实测：200 请求 / 100 件库存下可全部卖出；限流拦截率约 85%。
     * 注意：Guava 是单机限流，多实例部署请使用阶段 6 的分布式令牌桶。
     *
     * @param sid 商品ID
     * @return 剩余库存 / 被限流提示 / 失败原因
     */
    @GetMapping("/stage2/optimistic-order-limited/{sid}")
    public String createOptimisticOrderWithLimit(@PathVariable int sid) {
        // 非阻塞式获取令牌：1 秒内拿不到令牌则判定被限流
        if (!guavaRateLimiter.tryAcquire(GUAVA_RATE_LIMIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            LOGGER.warn("阶段2：请求被令牌桶限流，sid=[{}]", sid);
            return "你被限流了，真不幸，直接返回失败";
        }
        try {
            int stockLeft = orderService.createOptimisticOrder(sid);
            LOGGER.info("阶段2：限流后乐观锁下单成功，剩余库存=[{}]", stockLeft);
            return String.format("购买成功，剩余库存为：%d", stockLeft);
        } catch (Exception e) {
            LOGGER.error("阶段2：限流后乐观锁下单失败：[{}]", e.getMessage());
            return "购买失败：" + e.getMessage();
        }
    }

    /**
     * 阶段 2：悲观锁下单（SELECT ... FOR UPDATE + 事务）。
     * <p>
     * 方案：事务内 SELECT FOR UPDATE 锁行 → 扣减 → 创建订单，DB 串行化保证"卖得完"。
     * 实测：50~500 VUs QPS 稳定 328~340、业务成功率 94%~100%，是最稳的卖完策略；
     * 缺点是高并发下排队等待时间长（1000 VUs 时 QPS 降至 213）。
     *
     * @param sid 商品ID
     * @return 剩余库存 或失败原因
     */
    @GetMapping("/stage2/pessimistic-order/{sid}")
    public String createPessimisticOrder(@PathVariable int sid) {
        try {
            int stockLeft = orderService.createPessimisticOrder(sid);
            LOGGER.info("阶段2：悲观锁下单成功，剩余库存=[{}]", stockLeft);
            return String.format("购买成功，剩余库存为：%d", stockLeft);
        } catch (Exception e) {
            LOGGER.error("阶段2：悲观锁下单失败：[{}]", e.getMessage());
            return "购买失败：" + e.getMessage();
        }
    }

    // ==================================================================
    // 阶段 3：抢购接口隐藏 + 单用户限制频率
    // ==================================================================

    /**
     * 阶段 3-步骤1：获取抢购验签 hash（接口隐藏 / 接口加盐）。
     * <p>
     * 方案：校验抢购时段、用户、商品合法性后，生成 MD5(SALT+sid+userId) 写入 Redis（3600 秒），
     * 使抢购接口地址变成"一次性凭证"，防脚本直连刷单。
     *
     * @param sid    商品ID
     * @param userId 用户ID
     * @return 验签 hash 或失败原因
     */
    @GetMapping("/stage3/verify-hash")
    public String getVerifyHash(@RequestParam(value = "sid") Integer sid,
                                @RequestParam(value = "userId") Long userId) {
        try {
            String hash = userService.getVerifyHash(sid, userId);
            LOGGER.info("阶段3：验签hash生成成功，sid=[{}]，userId=[{}]", sid, userId);
            return String.format("请求抢购验证hash值为：%s", hash);
        } catch (Exception e) {
            LOGGER.error("阶段3：获取验证hash失败，原因：[{}]", e.getMessage());
            return "获取验证hash失败：" + e.getMessage();
        }
    }

    /**
     * 阶段 3-步骤2：携带验签 hash 下单。
     * <p>
     * 方案：比对请求携带的 verifyHash 与 Redis 中的值，一致才执行乐观锁扣减 + 创建订单。
     * 绕过方式：先调 /verify-hash 再立即下单（脚本仍可完成，但下单时刻被限制在抢购时间开始后）。
     *
     * @param sid        商品ID
     * @param userId     用户ID
     * @param verifyHash 步骤1获取的验签 hash
     * @return 剩余库存 或失败原因
     */
    @GetMapping("/stage3/verified-order")
    public String createOrderWithVerifiedUrl(@RequestParam(value = "sid") Integer sid,
                                             @RequestParam(value = "userId") Long userId,
                                             @RequestParam(value = "verifyHash") String verifyHash) {
        try {
            int stockLeft = orderService.createVerifiedOrder(sid, userId, verifyHash);
            LOGGER.info("阶段3：验签下单成功，剩余库存=[{}]", stockLeft);
            return String.format("购买成功，剩余库存为：%d", stockLeft);
        } catch (Exception e) {
            LOGGER.error("阶段3：验签下单失败：[{}]", e.getMessage());
            return "购买失败：" + e.getMessage();
        }
    }

    /**
     * 阶段 3-步骤3：验签下单 + 单用户限制访问频率。
     * <p>
     * 方案：Redis 按用户计数（60 秒窗口，超过 3 次即禁用），即便脚本持有合法 hash 也无法高频刷单。
     *
     * @param sid        商品ID
     * @param userId     用户ID
     * @param verifyHash 步骤1获取的验签 hash
     * @return 剩余库存 / 超频提示 / 失败原因
     */
    @GetMapping("/stage3/verified-order-limited")
    public String createOrderWithVerifiedUrlAndLimit(@RequestParam(value = "sid") Integer sid,
                                                     @RequestParam(value = "userId") Long userId,
                                                     @RequestParam(value = "verifyHash") String verifyHash) {
        try {
            int count = userService.addUserCount(userId);
            LOGGER.info("阶段3：用户截至该次的访问次数为：[{}]", count);
            boolean isBanned = userService.getUserIsBanned(userId);
            if (isBanned) {
                return "购买失败，超过频率限制";
            }
            int stockLeft = orderService.createVerifiedOrder(sid, userId, verifyHash);
            LOGGER.info("阶段3：验签+限频下单成功，剩余库存=[{}]", stockLeft);
            return String.format("购买成功，剩余库存为：%d", stockLeft);
        } catch (Exception e) {
            LOGGER.error("阶段3：验签+限频下单失败：[{}]", e.getMessage());
            return "购买失败：" + e.getMessage();
        }
    }

    // ==================================================================
    // 阶段 4：Redis 缓存 + 双写一致性 V1 ~ V5
    // ==================================================================

    /**
     * 阶段 4-对照实验：直接查数据库库存（无缓存基线）。
     * <p>
     * 实测：无缓存吞吐 668 QPS 且 5% 请求失败。
     *
     * @param sid 商品ID
     * @return 数据库剩余库存
     */
    @GetMapping("/stage4/stock-by-db/{sid}")
    public String getStockByDB(@PathVariable int sid) {
        int count = stockService.getStockCountByDB(sid);
        LOGGER.info("阶段4：数据库查询库存，sid=[{}]，count=[{}]", sid, count);
        return String.format("商品Id: %d 数据库剩余库存为：%d", sid, count);
    }

    /**
     * 阶段 4-对照实验：查缓存库存（未命中则回源 DB 并回填）。
     * <p>
     * 实测：有缓存吞吐 2177 QPS（约 3 倍提升），但引入缓存一致性问题。
     *
     * @param sid 商品ID
     * @return 缓存剩余库存
     */
    @GetMapping("/stage4/stock-by-cache/{sid}")
    public String getStockByCache(@PathVariable int sid) {
        Integer count = stockService.getStockCountByCache(sid);
        if (count == null) {
            count = stockService.getStockCountByDB(sid);
            stockService.setStockCountCache(sid, count);
            LOGGER.info("阶段4：缓存未命中，查询数据库并写入缓存，sid=[{}]，count=[{}]", sid, count);
        }
        return String.format("商品Id: %d 缓存剩余库存为：%d", sid, count);
    }

    /**
     * 阶段 4-V1：先删除缓存，再更新数据库（扣库）。
     * <p>
     * 缺陷：两步之间若有读请求，会把 DB 旧值回填缓存，产生脏数据（无过期时间则永不恢复）。
     * 实测：QPS 318，业务成功率 100%（缓存清理耗时相比锁等待可忽略）。
     *
     * @param sid 商品ID
     * @return 剩余库存 或失败原因
     */
    @GetMapping("/stage4/cache-v1/{sid}")
    public String createOrderWithCacheV1(@PathVariable int sid) {
        try {
            // 1. 删除库存缓存
            stockService.delStockCountCache(sid);
            // 2. 完成扣库存下单事务（悲观锁）
            int count = orderService.createPessimisticOrder(sid);
            LOGGER.info("阶段4-V1：下单成功，剩余库存=[{}]", count);
            return String.format("购买成功，剩余库存为：%d", count);
        } catch (Exception e) {
            LOGGER.error("阶段4-V1：下单失败：[{}]", e.getMessage());
            return "购买失败：" + e.getMessage();
        }
    }

    /**
     * 阶段 4-V2：先更新数据库（扣库），再删除缓存（业界推荐的基础策略）。
     * <p>
     * 改进：删除失败最多残留旧缓存；"读旧值回填"的脏数据窗口因写操作通常慢于读操作而概率极低。
     * 实测：QPS 319，业务成功率 100%。
     *
     * @param sid 商品ID
     * @return 剩余库存 或失败原因
     */
    @GetMapping("/stage4/cache-v2/{sid}")
    public String createOrderWithCacheV2(@PathVariable int sid) {
        try {
            // 1. 完成扣库存下单事务（悲观锁）
            int count = orderService.createPessimisticOrder(sid);
            // 2. 删除库存缓存
            stockService.delStockCountCache(sid);
            LOGGER.info("阶段4-V2：下单成功，剩余库存=[{}]", count);
            return String.format("购买成功，剩余库存为：%d", count);
        } catch (Exception e) {
            LOGGER.error("阶段4-V2：下单失败：[{}]", e.getMessage());
            return "购买失败：" + e.getMessage();
        }
    }

    /**
     * 阶段 4-V3：V1 + 延迟双删（先删缓存 → 扣库 → 异步再删一次）。
     * <p>
     * 方案：异步线程休眠 1 秒后二次删除，兜底清掉"删除与扣库之间"回填的脏缓存。
     * 实测：QPS 305，业务成功率 100%。
     *
     * @param sid 商品ID
     * @return 剩余库存 或失败原因
     */
    @GetMapping("/stage4/cache-v3/{sid}")
    public String createOrderWithCacheV3(@PathVariable int sid) {
        try {
            // 1. 删除库存缓存
            stockService.delStockCountCache(sid);
            // 2. 完成扣库存下单事务（悲观锁）
            int count = orderService.createPessimisticOrder(sid);
            // 3. 延迟指定时间后再次删除缓存（异步，不阻塞用户请求）
            submitDelayDeleteCache(sid);
            LOGGER.info("阶段4-V3：下单成功，剩余库存=[{}]", count);
            return String.format("购买成功，剩余库存为：%d", count);
        } catch (Exception e) {
            LOGGER.error("阶段4-V3：下单失败：[{}]", e.getMessage());
            return "购买失败：" + e.getMessage();
        }
    }

    /**
     * 阶段 4-V4：V2 + 延迟双删 + MQ 兜底删缓存（最终一致方案）。
     * <p>
     * 方案：扣库 → 删缓存 → 延迟双删；假设上述删除均失败，再发 delCache 队列由消费者重试删除。
     * 实测：QPS 269，业务成功率 100%；一致性的演进不提升吞吐（瓶颈仍在锁），只提升数据正确性。
     *
     * @param sid 商品ID
     * @return 购买结果
     */
    @GetMapping("/stage4/cache-v4/{sid}")
    public String createOrderWithCacheV4(@PathVariable int sid) {
        try {
            // 1. 完成扣库存下单事务（悲观锁）
            int count = orderService.createPessimisticOrder(sid);
            // 2. 删除库存缓存
            stockService.delStockCountCache(sid);
            // 3. 延迟指定时间后再次删除缓存（异步）
            submitDelayDeleteCache(sid);
            // 4. MQ 兜底：假设上述删除均失败，通知 delCache 队列重试删除，保证最终一致
            sendToDelCache(String.valueOf(sid));
            LOGGER.info("阶段4-V4：下单成功，剩余库存=[{}]", count);
            return "购买成功";
        } catch (Exception e) {
            LOGGER.error("阶段4-V4：下单失败：[{}]", e.getMessage());
            return "购买失败：" + e.getMessage();
        }
    }

    /**
     * 阶段 4-V5："全都要"组合（反面教材）。
     * <p>
     * 设计：乐观锁 + 用户校验 + 订单记录 + MQ 异步 + 缓存三件套全部堆砌。
     * 实测（原版）：QPS 仅 7~37、平均 RT 10 秒——原版在第 309 行残留 new ArrayList(10000) 死代码、
     * 在 MQ 消费逻辑中留有 Thread.sleep(10000) 模拟排队，导致性能崩塌。
     * 本教学版已移除上述缺陷代码，保留"技术堆砌"链路用于对比演示，结论不变：
     * 架构不是越复杂越好，而是越匹配场景越好。
     *
     * @param sid    商品ID
     * @param userId 用户ID
     * @return 购买结果
     */
    @GetMapping("/stage4/cache-v5")
    public String createOrderWithCacheV5(@RequestParam(value = "sid") Integer sid,
                                         @RequestParam(value = "userId") Long userId) {
        try {
            // 1. 乐观锁 + 用户校验 + 订单记录 + 异步下单（createOptimisticOrder 重载内部已含完整链路）
            int count = orderService.createOptimisticOrder(sid, userId);
            // 2. 删除库存缓存
            stockService.delStockCountCache(sid);
            // 3. 延迟指定时间后再次删除缓存
            submitDelayDeleteCache(sid);
            // 4. MQ 兜底删缓存
            sendToDelCache(String.valueOf(sid));
            LOGGER.info("阶段4-V5：下单成功，剩余库存=[{}]", count);
            return "购买成功";
        } catch (Exception e) {
            LOGGER.error("阶段4-V5：下单失败：[{}]", e.getMessage());
            return "购买失败：" + e.getMessage();
        }
    }

    // ==================================================================
    // 阶段 5：MQ 异步下单 + 结果轮询
    // ==================================================================

    /**
     * 阶段 5-步骤1：MQ 异步下单（接口不碰 DB，只查缓存 + 入队）。
     * <p>
     * 方案：查用户已购缓存 → 查库存缓存 → 发送 orderQueue 消息（含 orderId/sid/userId），立即返回。
     * 真正的扣库与落单由 OrderMqReceiver 异步消费处理（消费者内查表二次校验兜底）。
     * 实测：200 VUs 下 QPS 3944（悲观锁的 11.6 倍）、平均 RT 43ms（同步的 1/11）。
     *
     * @param sid    商品ID
     * @param userId 用户ID
     * @return 提交结果（含 orderId，供轮询使用）
     */
    @GetMapping("/stage5/order-with-mq")
    public String createUserOrderWithMq(@RequestParam(value = "sid") Integer sid,
                                        @RequestParam(value = "userId") Long userId) {
        try {
            // 1. 检查缓存中该用户是否已经下单过（Redis Set 去重）
            Boolean hasOrder = orderService.checkUserOrderInfoInCache(sid, userId);
            if (hasOrder != null && Boolean.TRUE.equals(hasOrder)) {
                LOGGER.info("阶段5：该用户已经抢购过，sid=[{}]，userId=[{}]", sid, userId);
                return "你已经抢购过了，不要太贪心.....";
            }
            // 2. 检查缓存中商品是否还有库存
            Integer count = stockService.getStockCount(sid);
            if (count == null || count == 0) {
                return "秒杀请求失败，库存不足.....";
            }
            // 3. 有库存则将 orderId、sid、userId 封装为消息体交给 MQ（缓存判定不可靠，消费者会查表兜底）
            String orderId = UUID.randomUUID().toString();
            JSONObject message = new JSONObject();
            message.put("orderId", orderId);
            message.put("sid", sid);
            message.put("userId", userId);
            sendToOrderQueue(message.toJSONString());
            LOGGER.info("阶段5：异步下单请求已提交，orderId=[{}]，sid=[{}]，userId=[{}]", orderId, sid, userId);
            return "秒杀请求提交成功，orderId=" + orderId;
        } catch (Exception e) {
            LOGGER.error("阶段5：异步下单异常：", e);
            return "秒杀请求失败，服务器正忙.....";
        }
    }

    /**
     * 阶段 5-步骤2：轮询查询订单是否处理完成（前端"排队中"体验）。
     * <p>
     * 方案：消费者下单成功后会写入 USER_HAS_ORDER 缓存，前端携带同样参数轮询本接口即可知晓结果。
     *
     * @param sid    商品ID
     * @param userId 用户ID
     * @return 抢购成功或继续排队提示
     */
    @GetMapping("/stage5/purchase-result")
    public String checkOrderByUserIdInCache(@RequestParam(value = "sid") Integer sid,
                                            @RequestParam(value = "userId") Long userId) {
        try {
            Boolean hasOrder = orderService.checkUserOrderInfoInCache(sid, userId);
            if (hasOrder != null && Boolean.TRUE.equals(hasOrder)) {
                return "恭喜您，已经抢购成功！";
            }
        } catch (Exception e) {
            LOGGER.error("阶段5：检查订单异常：", e);
        }
        return "很抱歉，你的订单尚未生成，继续排队吧您嘞。";
    }

    // ==================================================================
    // 阶段 6：工程化增强（分布式锁 / 幂等 / 分布式限流 / 熔断降级）
    // ==================================================================

    /**
     * 阶段 6：Redisson 分布式锁 + 乐观锁下单。
     * <p>
     * 方案：以商品为维度加可重入锁（等待 3 秒、持有 10 秒），把"高争用"削成"低争用"，
     * 锁内的乐观锁 CAS 几乎不再冲突；解锁放在 finally 中，防止死锁。
     * 对比：自研 SETNX 锁无自动续期，Redisson 提供 Watchdog 续期与安全释放。
     *
     * @param sid 商品ID
     * @return 剩余库存 / 获取锁失败提示 / 失败原因
     */
    @GetMapping("/stage6/locked-order/{sid}")
    public String createOrderWithDistributedLock(@PathVariable int sid) {
        String lockKey = LOCK_KEY_PREFIX + sid;
        boolean locked = distributedLockService.tryLock(lockKey, LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
        if (!locked) {
            LOGGER.warn("阶段6：获取分布式锁失败，lockKey=[{}]", lockKey);
            return "获取分布式锁失败，请稍后重试.....";
        }
        try {
            int stockLeft = orderService.createOptimisticOrder(sid);
            LOGGER.info("阶段6：分布式锁下单成功，剩余库存=[{}]", stockLeft);
            return String.format("购买成功，剩余库存为：%d", stockLeft);
        } catch (Exception e) {
            LOGGER.error("阶段6：分布式锁下单失败：[{}]", e.getMessage());
            return "购买失败：" + e.getMessage();
        } finally {
            distributedLockService.unlock(lockKey);
        }
    }

    /**
     * 阶段 6：幂等下单（防止重复提交 / MQ 重投）。
     * <p>
     * 方案：requestId 经 Redis SETNX 原子标记（5 分钟有效）防重复请求，
     * 叠加用户已购缓存检查；下单失败时移除标记允许重试，成功则保留标记拒绝重复提交。
     *
     * @param sid       商品ID
     * @param userId    用户ID
     * @param requestId 幂等请求ID（前端生成的 UUID）
     * @return 剩余库存 / 重复请求提示 / 失败原因
     */
    @GetMapping("/stage6/idempotent-order")
    public String createIdempotentOrder(@RequestParam(value = "sid") Integer sid,
                                        @RequestParam(value = "userId") Long userId,
                                        @RequestParam(value = "requestId") String requestId) {
        // 1. 幂等检查：SETNX 标记失败说明同一 requestId 已处理过
        if (!idempotencyService.checkAndMark(requestId)) {
            LOGGER.warn("阶段6：重复请求被幂等拦截，requestId=[{}]", requestId);
            return "重复请求：requestId 已被处理，请勿重复提交.....";
        }
        try {
            // 2. 用户维度检查：该用户是否已购（业务终态，不释放幂等标记）
            Boolean hasOrder = orderService.checkUserOrderInfoInCache(sid, userId);
            if (hasOrder != null && Boolean.TRUE.equals(hasOrder)) {
                return "你已经抢购过了，不要太贪心.....";
            }
            // 3. 乐观锁下单
            int stockLeft = orderService.createOptimisticOrder(sid);
            LOGGER.info("阶段6：幂等下单成功，剩余库存=[{}]", stockLeft);
            return String.format("购买成功，剩余库存为：%d", stockLeft);
        } catch (Exception e) {
            // 4. 下单失败（如库存冲突）：移除幂等标记，允许用户重试
            idempotencyService.removeIdempotentMark(requestId);
            LOGGER.error("阶段6：幂等下单失败：[{}]", e.getMessage());
            return "购买失败：" + e.getMessage();
        }
    }

    /**
     * 阶段 6：Redis + Lua 分布式令牌桶限流下单。
     * <p>
     * 方案：令牌桶状态存于 Redis（原子 Lua 脚本填充与消费），多实例共享同一配额，
     * 解决 Guava 单机限流在集群下"每台各放 10 个"的失效问题（容量 10，速率 10 个/秒）。
     *
     * @param sid 商品ID
     * @return 剩余库存 / 被限流提示 / 失败原因
     */
    @GetMapping("/stage6/token-bucket-order/{sid}")
    public String createOrderWithTokenBucket(@PathVariable int sid) {
        String bucketKey = TOKEN_BUCKET_KEY_PREFIX + sid;
        if (!distributedTokenBucketService.tryAcquire(bucketKey, TOKEN_BUCKET_CAPACITY, TOKEN_BUCKET_RATE)) {
            LOGGER.warn("阶段6：请求被分布式令牌桶限流，bucketKey=[{}]", bucketKey);
            return "你被限流了，真不幸，直接返回失败";
        }
        try {
            int stockLeft = orderService.createOptimisticOrder(sid);
            LOGGER.info("阶段6：分布式令牌桶下单成功，剩余库存=[{}]", stockLeft);
            return String.format("购买成功，剩余库存为：%d", stockLeft);
        } catch (Exception e) {
            LOGGER.error("阶段6：分布式令牌桶下单失败：[{}]", e.getMessage());
            return "购买失败：" + e.getMessage();
        }
    }

    /**
     * 阶段 6：受 DB 熔断保护的下单（含降级）。
     * <p>
     * 方案：下单操作经 Resilience4j DB 熔断器包装（慢调用率 60% 或错误率过高时熔断 60 秒），
     * 熔断打开时直接走降级逻辑，防止故障扩散拖垮整条链路。
     *
     * @param sid 商品ID
     * @return 剩余库存 或降级提示
     */
    @GetMapping("/stage6/order-with-circuit-breaker/{sid}")
    public String createOrderWithCircuitBreaker(@PathVariable int sid) {
        Integer stockLeft = circuitBreakerService.executeWithDbBreaker(
                () -> orderService.createOptimisticOrder(sid),
                () -> -1);
        if (stockLeft != null && stockLeft == -1) {
            LOGGER.warn("阶段6：DB 熔断器已打开，请求被降级，sid=[{}]", sid);
            return "系统繁忙，已触发熔断降级，请稍后重试.....";
        }
        LOGGER.info("阶段6：熔断保护下单成功，剩余库存=[{}]", stockLeft);
        return String.format("购买成功，剩余库存为：%d", stockLeft);
    }

    /**
     * 阶段 6：查询各熔断器当前状态（监控用）。
     *
     * @return 熔断器名称 → 状态（CLOSED / OPEN / HALF_OPEN / DISABLED / FORCED_OPEN）
     */
    @GetMapping("/stage6/circuit-breaker-status")
    public ApiResponse<Map<String, String>> getCircuitBreakerStatus() {
        Map<String, String> statusMap = circuitBreakerService.getCircuitBreakerStatus();
        LOGGER.info("阶段6：查询熔断器状态：{}", statusMap);
        return ApiResponse.success(statusMap);
    }

    // ==================================================================
    // 内部支撑：线程池、延迟双删任务、MQ 发送
    // ==================================================================

    /**
     * 提交延迟双删任务；线程池拒绝时改由 MQ 兜底删除，保证最终一致。
     *
     * @param sid 商品ID
     */
    private void submitDelayDeleteCache(int sid) {
        try {
            DELAY_DELETE_EXECUTOR.execute(new DelayDeleteCacheTask(sid, stockService, rabbitTemplate));
        } catch (RejectedExecutionException e) {
            LOGGER.warn("延迟双删任务被线程池拒绝，改由 MQ 兜底删除，sid=[{}]", sid);
            sendToDelCache(String.valueOf(sid));
        }
    }

    /**
     * 向消息队列 orderQueue 发送异步下单消息。
     *
     * @param message 消息体（JSON：orderId/sid/userId）
     */
    private void sendToOrderQueue(String message) {
        LOGGER.info("阶段5：发送订单消息到 orderQueue：[{}]", message);
        rabbitTemplate.convertAndSend(ORDER_QUEUE, message);
    }

    /**
     * 向消息队列 delCache 发送缓存删除消息（兜底重试）。
     *
     * @param message 商品ID字符串
     */
    private void sendToDelCache(String message) {
        LOGGER.info("阶段4：发送缓存删除消息到 delCache：[{}]", message);
        rabbitTemplate.convertAndSend(DEL_CACHE_QUEUE, message);
    }

    /**
     * 应用关闭时优雅停止延迟双删线程池。
     */
    @PreDestroy
    public void shutdownDelayDeleteExecutor() {
        DELAY_DELETE_EXECUTOR.shutdown();
        LOGGER.info("SeckillController：延迟双删线程池已关闭");
    }

    /**
     * 延迟双删任务：休眠 1 秒后再次删除库存缓存；
     * 删除失败时发送 delCache 消息队列进行兜底重试，保证最终一致。
     */
    private static class DelayDeleteCacheTask implements Runnable {

        private final int sid;
        private final StockService stockService;
        private final AmqpTemplate rabbitTemplate;

        DelayDeleteCacheTask(int sid, StockService stockService, AmqpTemplate rabbitTemplate) {
            this.sid = sid;
            this.stockService = stockService;
            this.rabbitTemplate = rabbitTemplate;
        }

        @Override
        public void run() {
            try {
                LOGGER.info("延迟双删：等待 {} ms 后再次删除库存缓存，sid=[{}]", DELAY_MILLISECONDS, sid);
                Thread.sleep(DELAY_MILLISECONDS);
                stockService.delStockCountCache(sid);
                LOGGER.info("延迟双删完成，sid=[{}]", sid);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOGGER.warn("延迟双删任务被中断，sid=[{}]", sid);
            } catch (Exception e) {
                LOGGER.error("延迟双删失败，改由 MQ 兜底，sid=[{}]，原因=[{}]", sid, e.getMessage(), e);
                sendToDelCacheQuietly();
            }
        }

        /**
         * 发送 delCache 兜底消息（本方法不得抛出异常）。
         */
        private void sendToDelCacheQuietly() {
            try {
                rabbitTemplate.convertAndSend(DEL_CACHE_QUEUE, String.valueOf(sid));
                LOGGER.info("已发送 delCache 兜底消息，sid=[{}]", sid);
            } catch (Exception ex) {
                LOGGER.error("发送 delCache 兜底消息失败，sid=[{}]", sid, ex);
            }
        }
    }

    /**
     * 命名线程工厂：为延迟双删线程池创建带业务含义名称的守护线程，便于问题回溯。
     */
    private static class NamedThreadFactory implements ThreadFactory {

        private final String namePrefix;
        private final AtomicInteger threadNumber = new AtomicInteger(1);

        NamedThreadFactory(String namePrefix) {
            this.namePrefix = namePrefix;
        }

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, namePrefix + threadNumber.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }
    }
}
