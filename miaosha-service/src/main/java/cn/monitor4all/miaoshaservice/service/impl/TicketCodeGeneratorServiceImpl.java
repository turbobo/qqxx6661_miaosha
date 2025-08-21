package cn.monitor4all.miaoshaservice.service.impl;

import cn.monitor4all.miaoshaservice.service.TicketCodeGeneratorService;
import cn.monitor4all.miaoshaservice.service.SequenceGeneratorService;
import cn.monitor4all.miaoshadao.mapper.TicketOrderMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 票券编码生成服务实现类
 * 提供多种策略生成唯一票券编码，确保编码的唯一性
 */
@Service
public class TicketCodeGeneratorServiceImpl implements TicketCodeGeneratorService {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(TicketCodeGeneratorServiceImpl.class);
    
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    
    @Resource
    private TicketOrderMapper ticketOrderMapper;
    
    @Resource
    private SequenceGeneratorService sequenceGeneratorService;
    
    // 编码生成策略
    private String currentStrategy = "Redis序列号";
    
    @Override
    public String generateUniqueTicketCode(String userId, String date) {
        return generateUniqueTicketCode(userId, date, 3);
    }
    
    @Override
    public String generateUniqueTicketCode(String userId, String date, int maxRetries) {
        for (int i = 0; i < maxRetries; i++) {
            String ticketCode = generateTicketCode(userId, date);
            
            if (isTicketCodeUnique(ticketCode)) {
                return ticketCode;
            }
            
            LOGGER.warn("票券编码冲突，重试第{}次: {}", i + 1, ticketCode);
            
            // 重试前等待一小段时间，避免连续冲突
            try {
                Thread.sleep(10 + (int)(Math.random() * 20));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        // 所有重试都失败，使用兜底方案
        LOGGER.error("票券编码生成重试{}次后仍冲突，使用兜底方案", maxRetries);
        currentStrategy = "兜底方案";
        return generateTicketCodeWithTimestamp(userId, date) + "_" + System.nanoTime();
    }
    
    @Override
    public boolean isTicketCodeUnique(String ticketCode) {
        try {
            // 检查数据库中是否已存在
            Object existingOrder = ticketOrderMapper.selectByTicketCode(ticketCode);
            if (existingOrder != null) {
                return false;
            }
            
            // 检查Redis缓存中是否已存在（可选）
            String cacheKey = "ticket:code:" + ticketCode;
            Boolean exists = stringRedisTemplate.hasKey(cacheKey);
            if (Boolean.TRUE.equals(exists)) {
                return false;
            }
            
            // 将编码标记为已使用（设置短期过期时间）
            stringRedisTemplate.opsForValue().set(cacheKey, "1", 1, TimeUnit.HOURS);
            
            return true;
        } catch (Exception e) {
            LOGGER.error("验证票券编码唯一性失败: {}", e.getMessage(), e);
            // 验证失败时，为了安全起见，返回false
            return false;
        }
    }
    
    @Override
    public String getGenerationStrategy() {
        return currentStrategy;
    }
    
    /**
     * 生成票券编码（多策略）
     */
    private String generateTicketCode(String userId, String date) {
        try {
            // 方案1：使用Redis序列号（推荐）
            String ticketCode = generateTicketCodeWithRedisSequence(userId, date);
            if (ticketCode != null) {
                currentStrategy = "Redis序列号";
                return ticketCode;
            }
            
            // 方案2：使用时间戳 + 纳秒（备选）
            currentStrategy = "时间戳+纳秒";
            return generateTicketCodeWithTimestamp(userId, date);
            
        } catch (Exception e) {
            LOGGER.warn("Redis序列号生成失败，使用备选方案: {}", e.getMessage());
            // 方案3：使用UUID + 时间戳（兜底）
            currentStrategy = "UUID+时间戳";
            return generateTicketCodeWithUUID(userId, date);
        }
    }
    
    /**
     * 方案1：使用Redis序列号生成票券编码（推荐）
     * 格式：T + 日期 + 序列号 + 用户ID后4位 + 随机数
     * 改进：基于上次序列号递增，支持持久化和连续性
     */
    private String generateTicketCodeWithRedisSequence(String userId, String date) {
        try {
            String dateStr = LocalDate.parse(date).format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            String userSuffix = String.valueOf(userId).substring(Math.max(0, String.valueOf(userId).length() - 4));
            
            // 使用专业的序列号生成服务
            long sequence = sequenceGeneratorService.getNextSequence(date);
            
            // 生成随机数
            String randomStr = String.valueOf((int)(Math.random() * 1000));
            
            // 格式：T + 日期 + 序列号(6位) + 用户ID后4位 + 随机数(3位)
            String ticketCode = String.format("T%s%06d%s%03d", dateStr, sequence, userSuffix, Integer.parseInt(randomStr));
            
            LOGGER.debug("生成票券编码，日期: {}, 序列号: {}, 编码: {}", date, sequence, ticketCode);
            
            return ticketCode;
            
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
        return String.format("T%s%08d%s%03d", dateStr, timestamp % 100000000, userSuffix, (int)(nanoTime % 1000));
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
        int timestampSuffix = (int)(timestamp % 1000);
        
        // 格式：T + 日期 + UUID前8位 + 用户ID后4位 + 时间戳后3位
        return String.format("T%s%s%s%03d", dateStr, uuidPrefix, userSuffix, timestampSuffix);
    }
}
