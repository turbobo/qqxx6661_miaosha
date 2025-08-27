package cn.monitor4all.miaoshaservice.service.impl;

import cn.monitor4all.miaoshadao.dao.TicketEntity;
import cn.monitor4all.miaoshadao.dao.TicketPurchaseRecord;
import cn.monitor4all.miaoshadao.mapper.TicketEntityMapper;
import cn.monitor4all.miaoshadao.mapper.TicketPurchaseRecordMapper;
import cn.monitor4all.miaoshadao.model.PurchaseRecord;
import cn.monitor4all.miaoshadao.model.Ticket;
import cn.monitor4all.miaoshaservice.service.TicketCacheManager;
import cn.monitor4all.miaoshaservice.service.AsyncCacheDeleteService;
import com.alibaba.fastjson.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 票券缓存管理器实现类
 * 提供票券缓存的基本操作方法
 */
@Service
public class TicketCacheManagerImpl implements TicketCacheManager {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(TicketCacheManagerImpl.class);
    
    // 缓存过期时间：1小时
    private static final long CACHE_EXPIRE_TIME = 3600L;
    
    // 票券缓存key前缀
    private static final String TICKET_CACHE_PREFIX = "ticket:";
    
    // 票券列表缓存key
    private static final String TICKET_LIST_CACHE_KEY = "ticket:list";
    
    // 购买记录缓存key前缀
    private static final String PURCHASE_RECORD_CACHE_PREFIX = "purchase:";
    
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    
    @Resource
    private TicketPurchaseRecordMapper ticketPurchaseRecordMapper;
    
    @Resource
    private TicketEntityMapper ticketEntityMapper;
    
    @Override
    public Ticket getTicketWithFallback(String date) {
        try {
            // 1. 先从缓存获取
            String key = TICKET_CACHE_PREFIX + date;
            String ticketJson = stringRedisTemplate.opsForValue().get(key);
            
            if (ticketJson != null) {
                Ticket ticket = JSON.parseObject(ticketJson, Ticket.class);
                LOGGER.debug("从缓存获取票券成功，日期: {}, 票券: {}", date, ticket);
                return ticket;
            }
            
            LOGGER.debug("缓存中未找到票券，尝试从数据库获取，日期: {}", date);
            
            // 2. 缓存中没有，从数据库获取
            TicketEntity ticketEntity = ticketEntityMapper.selectByDate(date);
            if (ticketEntity != null) {
                // 3. 转换为Ticket对象
                Ticket ticket = convertToTicket(ticketEntity);
                
                // 4. 更新到缓存
                saveTicket(date, ticket);
                
                LOGGER.info("从数据库获取票券成功并更新缓存，日期: {}, 票券: {}", date, ticket);
                return ticket;
            }
            
            LOGGER.debug("数据库中未找到票券，日期: {}", date);
            return null;
            
        } catch (Exception e) {
            LOGGER.error("获取票券失败，日期: {}", date, e);
            return null;
        }
    }
    
    @Override
    public void saveTicket(String date, Ticket ticket) {
        try {
            String key = TICKET_CACHE_PREFIX + date;
            String ticketJson = JSON.toJSONString(ticket);
            
            stringRedisTemplate.opsForValue().set(key, ticketJson, CACHE_EXPIRE_TIME, TimeUnit.SECONDS);
            
            LOGGER.debug("票券保存到缓存成功，日期: {}, key: {}", date, key);
        } catch (Exception e) {
            LOGGER.error("票券保存到缓存失败，日期: {}", date, e);
        }
    }

    @Resource
    private AsyncCacheDeleteService asyncCacheDeleteService;
    
    // 使用双重异步删除：先线程池，再队列
    @Override
    public void deleteTicket(String date) {
        try {
            String key = TICKET_CACHE_PREFIX + date;
            
            // 使用双重异步删除：先线程池，再队列
            asyncCacheDeleteService.deleteCacheDualAsync(key);
            
            LOGGER.info("票券缓存删除任务已提交（双重异步），日期: {}, key: {}", date, key);
            
        } catch (Exception e) {
            LOGGER.error("提交票券缓存删除任务失败，日期: {}, key: {}", date, TICKET_CACHE_PREFIX + date, e);
            
            // 异常时使用队列删除作为兜底
            try {
                String key = TICKET_CACHE_PREFIX + date;
                asyncCacheDeleteService.deleteCacheByQueue(key);
                LOGGER.info("使用队列删除作为兜底，日期: {}, key: {}", date, key);
            } catch (Exception ex) {
                LOGGER.error("队列删除兜底也失败，日期: {}, key: {}", date, TICKET_CACHE_PREFIX + date, ex);
            }
        }
    }
    
    @Override
    public List<Ticket> getTicketList() {
        try {
            // 先尝试从缓存获取票券列表
            List<Ticket> tickets = getTicketsFromRecentDates();
            
//            if () {
//                List<Ticket> tickets = JSON.parseArray(ticketListJson, Ticket.class);
//                LOGGER.debug("从缓存获取票券列表成功，数量: {}", tickets != null ? tickets.size() : 0);
//                return tickets;
//            }
//
//            // 如果缓存中没有票券列表，则从最近3天的日期分别获取票券信息
//            LOGGER.debug("缓存中未找到票券列表，尝试从最近3天日期分别获取");
//            List<Ticket> tickets = getTicketsFromRecentDates();
//
//            if (tickets != null && !tickets.isEmpty()) {
//                // 将获取到的票券列表保存到缓存
//                saveTicketList(tickets);
//                LOGGER.info("从最近3天日期获取票券信息成功，数量: {}, 已保存到缓存", tickets.size());
//            }
            
            return tickets;
        } catch (Exception e) {
            LOGGER.error("从缓存获取票券列表失败", e);
            return null;
        }
    }
    
    /**
     * 从最近3天日期分别获取票券信息
     * @return 票券列表
     */
    private List<Ticket> getTicketsFromRecentDates() {
        try {
            // 计算最近3天的日期
            LocalDate today = LocalDate.now();
            String todayStr = today.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            String tomorrowStr = today.plusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            String dayAfterTomorrowStr = today.plusDays(2).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            
            LOGGER.debug("开始获取最近3天票券信息，日期: {}, {}, {}", todayStr, tomorrowStr, dayAfterTomorrowStr);
            
            List<Ticket> tickets = new ArrayList<>();
            
            // 分别获取每个日期的票券信息
            Ticket todayTicket = getTicketWithFallback(todayStr);
            if (todayTicket != null) {
                tickets.add(todayTicket);
                LOGGER.debug("获取今日票券成功: {}", todayTicket.getDate());
            }
            
            Ticket tomorrowTicket = getTicketWithFallback(tomorrowStr);
            if (tomorrowTicket != null) {
                tickets.add(tomorrowTicket);
                LOGGER.debug("获取明日票券成功: {}", tomorrowTicket.getDate());
            }
            
            Ticket dayAfterTomorrowTicket = getTicketWithFallback(dayAfterTomorrowStr);
            if (dayAfterTomorrowTicket != null) {
                tickets.add(dayAfterTomorrowTicket);
                LOGGER.debug("获取后日票券成功: {}", dayAfterTomorrowTicket.getDate());
            }
            
            LOGGER.info("从最近3天日期获取票券信息完成，成功获取{}张票券", tickets.size());
            return tickets;
            
        } catch (Exception e) {
            LOGGER.error("从最近3天日期获取票券信息失败", e);
            return new ArrayList<>();
        }
    }
    
    @Override
    public void saveTicketList(List<Ticket> tickets) {
        try {
            if (tickets != null && !tickets.isEmpty()) {
                String ticketListJson = JSON.toJSONString(tickets);
                
                stringRedisTemplate.opsForValue().set(TICKET_LIST_CACHE_KEY, ticketListJson, CACHE_EXPIRE_TIME, TimeUnit.SECONDS);
                
                LOGGER.debug("票券列表保存到缓存成功，数量: {}, key: {}", tickets.size(), TICKET_LIST_CACHE_KEY);
            }
        } catch (Exception e) {
            LOGGER.error("票券列表保存到缓存失败", e);
        }
    }
    
    @Override
    public void addPurchaseRecord(Long userId, String date, PurchaseRecord record) {
        try {
            String key = PURCHASE_RECORD_CACHE_PREFIX + userId + ":" + date;
            
            // 保存到缓存
            String recordJson = JSON.toJSONString(record);
            stringRedisTemplate.opsForValue().set(key, recordJson, CACHE_EXPIRE_TIME, TimeUnit.SECONDS);
            
            LOGGER.debug("购买记录添加到缓存成功，用户ID: {}, 日期: {}, key: {}", userId, date, key);
        } catch (Exception e) {
            LOGGER.error("购买记录添加到缓存失败，用户ID: {}, 日期: {}", userId, date, e);
        }
    }
    
    @Override
    public PurchaseRecord getPurchaseRecord(Long userId, String date) {
        try {
            String key = PURCHASE_RECORD_CACHE_PREFIX + userId + ":" + date;
            String recordJson = stringRedisTemplate.opsForValue().get(key);
            
            if (recordJson != null) {
                PurchaseRecord record = JSON.parseObject(recordJson, PurchaseRecord.class);
                LOGGER.debug("从缓存获取购买记录成功，用户ID: {}, 日期: {}, key: {}", userId, date, key);
                return record;
            }
            
            LOGGER.debug("缓存中未找到购买记录，用户ID: {}, 日期: {}", userId, date);
            return null;
        } catch (Exception e) {
            LOGGER.error("从缓存获取购买记录失败，用户ID: {}, 日期: {}", userId, date, e);
            return null;
        }
    }
    
    @Override
    public List<PurchaseRecord> getPurchaseRecords(Long userId) {
        try {
            // 由于现在缓存key包含了日期，需要获取用户的所有购买记录
            // 这里可以通过pattern匹配来获取所有相关的key
            String pattern = PURCHASE_RECORD_CACHE_PREFIX + userId + ":*";
            Set<String> keys = stringRedisTemplate.keys(pattern);
            
            if (keys != null && !keys.isEmpty()) {
                List<PurchaseRecord> records = new ArrayList<>();
                for (String key : keys) {
                    String recordJson = stringRedisTemplate.opsForValue().get(key);
                    if (recordJson != null) {
                        PurchaseRecord record = JSON.parseObject(recordJson, PurchaseRecord.class);
                        records.add(record);
                    }
                }
                
                LOGGER.debug("从缓存获取用户所有购买记录成功，用户ID: {}, 数量: {}", userId, records.size());
                return records;
            }
            
            LOGGER.debug("缓存中未找到用户购买记录，用户ID: {}", userId);
            return new ArrayList<>();
        } catch (Exception e) {
            LOGGER.error("从缓存获取用户购买记录失败，用户ID: {}", userId, e);
            return new ArrayList<>();
        }
    }
    
    @Override
    public PurchaseRecord getPurchaseRecordWithFallback(Long userId, String date) {
        try {
            // 首先尝试从缓存获取
            PurchaseRecord cachedRecord = getPurchaseRecord(userId, date);
            if (cachedRecord != null) {
                LOGGER.debug("从缓存获取购买记录成功，用户ID: {}, 日期: {}", userId, date);
                return cachedRecord;
            }
            
            // 缓存中没有，尝试从数据库获取
            LOGGER.debug("缓存中未找到购买记录，尝试从数据库获取，用户ID: {}, 日期: {}", userId, date);
            PurchaseRecord dbRecord = getPurchaseRecordFromDatabase(userId, date);
            
            if (dbRecord != null) {
                // 将数据库数据同步到缓存
                addPurchaseRecord(userId, date, dbRecord);
                LOGGER.debug("从数据库获取购买记录成功并同步到缓存，用户ID: {}, 日期: {}", userId, date);
                return dbRecord;
            }
            
            // 数据库也没有数据，返回null
            LOGGER.debug("数据库中也未找到购买记录，用户ID: {}, 日期: {}", userId, date);
            return null;
            
        } catch (Exception e) {
            LOGGER.error("获取购买记录失败，用户ID: {}, 日期: {}", userId, date, e);
            return null;
        }
    }
    
    @Override
    public List<PurchaseRecord> getPurchaseRecordsWithFallback(Long userId) {
        try {
            // 首先尝试从缓存获取
            List<PurchaseRecord> cachedRecords = getPurchaseRecords(userId);
            if (cachedRecords != null && !cachedRecords.isEmpty()) {
                LOGGER.debug("从缓存获取购买记录成功，用户ID: {}, 数量: {}", userId, cachedRecords.size());
                return cachedRecords;
            }
            
            // 缓存中没有，尝试从数据库获取
            LOGGER.debug("缓存中未找到购买记录，尝试从数据库获取，用户ID: {}", userId);
            List<PurchaseRecord> dbRecords = getPurchaseRecordsFromDatabase(userId);
            
            if (dbRecords != null && !dbRecords.isEmpty()) {
                // 将数据库数据同步到缓存
                savePurchaseRecordsToCache(userId, dbRecords);
                LOGGER.debug("从数据库获取购买记录成功并同步到缓存，用户ID: {}, 数量: {}", userId, dbRecords.size());
                return dbRecords;
            }
            
            // 数据库也没有数据，返回空列表
            LOGGER.debug("数据库中也未找到购买记录，用户ID: {}", userId);
            return new ArrayList<>();
            
        } catch (Exception e) {
            LOGGER.error("获取购买记录失败，用户ID: {}", userId, e);
            return new ArrayList<>();
        }
    }
    
    /**
     * 从数据库获取用户的购买记录
     * @param userId 用户ID
     * @return 购买记录列表
     */
    private List<PurchaseRecord> getPurchaseRecordsFromDatabase(Long userId) {
        try {
            // 从数据库查询用户的购买记录
            List<TicketPurchaseRecord> dbRecords = ticketPurchaseRecordMapper.selectByUserId(userId);
            
            if (dbRecords != null && !dbRecords.isEmpty()) {
                // 将数据库实体转换为前端模型
                List<PurchaseRecord> purchaseRecords = new ArrayList<>();
                for (TicketPurchaseRecord dbRecord : dbRecords) {
                    PurchaseRecord record = new PurchaseRecord(
                        dbRecord.getUserId(),
                        java.time.LocalDate.parse(dbRecord.getTicketDate()),
                        dbRecord.getTicketCode()
                    );
                    purchaseRecords.add(record);
                }
                
                LOGGER.debug("从数据库获取购买记录成功，用户ID: {}, 数量: {}", userId, purchaseRecords.size());
                return purchaseRecords;
            }
            
            LOGGER.debug("数据库中未找到购买记录，用户ID: {}", userId);
            return new ArrayList<>();
            
        } catch (Exception e) {
            LOGGER.error("从数据库获取购买记录失败，用户ID: {}", userId, e);
            return new ArrayList<>();
        }
    }
    
    /**
     * 从数据库获取用户的指定日期购买记录
     * @param userId 用户ID
     * @param date 购买日期
     * @return 购买记录
     */
    private PurchaseRecord getPurchaseRecordFromDatabase(Long userId, String date) {
        try {
            // 从数据库查询用户的指定日期购买记录
            TicketPurchaseRecord dbRecord = ticketPurchaseRecordMapper.selectByUserIdAndDate(userId, date);
            
            if (dbRecord != null) {
                // 将数据库实体转换为前端模型
                PurchaseRecord record = new PurchaseRecord(
                    dbRecord.getUserId(),
                    java.time.LocalDate.parse(dbRecord.getTicketDate()),
                    dbRecord.getTicketCode()
                );
                
                LOGGER.debug("从数据库获取购买记录成功，用户ID: {}, 日期: {}", userId, date);
                return record;
            }
            
            LOGGER.debug("数据库中未找到购买记录，用户ID: {}, 日期: {}", userId, date);
            return null;
            
        } catch (Exception e) {
            LOGGER.error("从数据库获取购买记录失败，用户ID: {}, 日期: {}", userId, date, e);
            return null;
        }
    }
    
    /**
     * 将购买记录保存到缓存
     * @param userId 用户ID
     * @param records 购买记录列表
     */
    private void savePurchaseRecordsToCache(Long userId, List<PurchaseRecord> records) {
        try {
            if (records != null && !records.isEmpty()) {
                for (PurchaseRecord record : records) {
                    String date = record.getDate().toString();
                    addPurchaseRecord(userId, date, record);
                }
                LOGGER.debug("购买记录同步到缓存成功，用户ID: {}, 数量: {}", userId, records.size());
            }
        } catch (Exception e) {
            LOGGER.error("购买记录同步到缓存失败，用户ID: {}", userId, e);
        }
    }
    
    @Override
    public void deletePurchaseRecord(Long userId, String date) {
        try {
            String key = PURCHASE_RECORD_CACHE_PREFIX + userId + ":" + date;
            stringRedisTemplate.delete(key);
            LOGGER.debug("购买记录缓存删除成功，用户ID: {}, 日期: {}, key: {}", userId, date, key);
        } catch (Exception e) {
            LOGGER.error("购买记录缓存删除失败，用户ID: {}, 日期: {}", userId, date, e);
        }
    }
    
    @Override
    public void deleteTicketList() {
        try {
            stringRedisTemplate.delete(TICKET_LIST_CACHE_KEY);
            LOGGER.debug("票券列表缓存删除成功，key: {}", TICKET_LIST_CACHE_KEY);
        } catch (Exception e) {
            LOGGER.error("票券列表缓存删除失败", e);
        }
    }
    
    @Override
    public void clearAllTicketCache() {
        try {
            // 删除票券列表缓存
            Boolean deleted = stringRedisTemplate.delete(TICKET_LIST_CACHE_KEY);
            LOGGER.info("删除票券列表缓存: {}", Boolean.TRUE.equals(deleted) ? "成功" : "失败");
            
            // 删除所有票券缓存（这里可以根据实际需求优化，比如使用pattern匹配删除）
            // 由于Redis的keys命令在生产环境中要谨慎使用，这里只删除列表缓存
            // 如果需要删除所有票券缓存，建议使用定时任务或者在业务逻辑中逐个删除
            
        } catch (Exception e) {
            LOGGER.error("清空票券缓存失败", e);
        }
    }
    
    @Override
    public boolean isRedisConnected() {
        try {
            // 尝试执行一个简单的Redis命令来检查连接状态
            String testKey = "test:connection";
            stringRedisTemplate.opsForValue().set(testKey, "test", 1, TimeUnit.SECONDS);
            String result = stringRedisTemplate.opsForValue().get(testKey);
            
            boolean connected = "test".equals(result);
            LOGGER.debug("Redis连接状态检查: {}", connected ? "正常" : "异常");
            return connected;
        } catch (Exception e) {
            LOGGER.error("Redis连接状态检查失败", e);
            return false;
        }
    }
    
    /**
     * 将TicketEntity转换为Ticket对象
     * @param ticketEntity 数据库实体
     * @return Ticket对象
     */
    private Ticket convertToTicket(TicketEntity ticketEntity) {
        if (ticketEntity == null) {
            return null;
        }
        
        // 使用Ticket的构造函数，它会自动计算remaining
        Ticket ticket = new Ticket(ticketEntity.getDate(), ticketEntity.getTotalCount());
        
        // 手动设置remaining，因为构造函数会将其设置为total
        ticket.setRemaining(ticketEntity.getRemainingCount());
        
        return ticket;
    }
}
