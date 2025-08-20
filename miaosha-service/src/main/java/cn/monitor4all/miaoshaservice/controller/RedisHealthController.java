package cn.monitor4all.miaoshaservice.controller;

import cn.monitor4all.miaoshaservice.service.TicketCacheManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Redis健康检查控制器
 */
@RestController
@RequestMapping("/api/redis")
public class RedisHealthController {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(RedisHealthController.class);
    
    @Resource
    private TicketCacheManager ticketCacheManager;
    
    /**
     * 检查Redis连接状态
     * @return 连接状态信息
     */
    @GetMapping("/health")
    public Map<String, Object> checkRedisHealth() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            boolean isConnected = ticketCacheManager.isRedisConnected();
            result.put("status", isConnected ? "UP" : "DOWN");
            result.put("message", isConnected ? "Redis连接正常" : "Redis连接异常");
            result.put("timestamp", System.currentTimeMillis());
            
            if (isConnected) {
                LOGGER.info("Redis健康检查通过");
            } else {
                LOGGER.warn("Redis健康检查失败");
            }
            
        } catch (Exception e) {
            LOGGER.error("Redis健康检查异常: {}", e.getMessage(), e);
            result.put("status", "ERROR");
            result.put("message", "Redis健康检查异常: " + e.getMessage());
            result.put("timestamp", System.currentTimeMillis());
        }
        
        return result;
    }
    
    /**
     * 清空所有票券缓存
     * @return 操作结果
     */
    @GetMapping("/cache/clear")
    public Map<String, Object> clearTicketCache() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            ticketCacheManager.clearAllTicketCache();
            result.put("status", "SUCCESS");
            result.put("message", "票券缓存清空成功");
            result.put("timestamp", System.currentTimeMillis());
            
            LOGGER.info("票券缓存清空成功");
            
        } catch (Exception e) {
            LOGGER.error("清空票券缓存失败: {}", e.getMessage(), e);
            result.put("status", "ERROR");
            result.put("message", "清空票券缓存失败: " + e.getMessage());
            result.put("timestamp", System.currentTimeMillis());
        }
        
        return result;
    }
    
    /**
     * 获取Redis缓存统计信息
     * @return 缓存统计信息
     */
    @GetMapping("/cache/stats")
    public Map<String, Object> getCacheStats() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // 检查Redis连接
            boolean isConnected = ticketCacheManager.isRedisConnected();
            result.put("redisConnected", isConnected);
            
            if (isConnected) {
                // 获取票券列表缓存
                Object ticketList = ticketCacheManager.getTicketList();
                result.put("ticketListCached", ticketList != null);
                result.put("ticketListSize", ticketList instanceof java.util.List ? ((java.util.List<?>) ticketList).size() : 0);
                
                // 获取今日票券缓存
                String today = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                Object todayTicket = ticketCacheManager.getTicketWithFallback(today);
                result.put("todayTicketCached", todayTicket != null);
                
                result.put("status", "SUCCESS");
                result.put("message", "缓存统计信息获取成功");
            } else {
                result.put("status", "ERROR");
                result.put("message", "Redis连接异常，无法获取缓存统计信息");
            }
            
            result.put("timestamp", System.currentTimeMillis());
            
        } catch (Exception e) {
            LOGGER.error("获取缓存统计信息失败: {}", e.getMessage(), e);
            result.put("status", "ERROR");
            result.put("message", "获取缓存统计信息失败: " + e.getMessage());
            result.put("timestamp", System.currentTimeMillis());
        }
        
        return result;
    }
}
