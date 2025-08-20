package cn.monitor4all.miaoshaservice.controller;

import cn.monitor4all.miaoshadao.mapper.TicketEntityMapper;
import cn.monitor4all.miaoshadao.mapper.TicketPurchaseRecordMapper;
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
 * 依赖注入测试控制器
 */
@RestController
@RequestMapping("/api/test")
public class DependencyTestController {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(DependencyTestController.class);
    
    @Resource
    private TicketEntityMapper ticketEntityMapper;
    
    @Resource
    private TicketPurchaseRecordMapper ticketPurchaseRecordMapper;
    
    @Resource
    private TicketCacheManager ticketCacheManager;
    
    /**
     * 测试依赖注入状态
     * @return 依赖注入状态信息
     */
    @GetMapping("/dependency")
    public Map<String, Object> testDependency() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // 检查各个组件的依赖注入状态
            boolean ticketEntityMapperInjected = ticketEntityMapper != null;
            boolean ticketPurchaseRecordMapperInjected = ticketPurchaseRecordMapper != null;
            boolean ticketCacheManagerInjected = ticketCacheManager != null;
            
            result.put("ticketEntityMapper", ticketEntityMapperInjected ? "已注入" : "未注入");
            result.put("ticketPurchaseRecordMapper", ticketPurchaseRecordMapperInjected ? "已注入" : "未注入");
            result.put("ticketCacheManager", ticketCacheManagerInjected ? "已注入" : "未注入");
            
            // 如果所有依赖都注入成功，尝试进行简单的数据库连接测试
            if (ticketEntityMapperInjected && ticketPurchaseRecordMapperInjected) {
                try {
                    // 尝试查询数据库，验证连接是否正常
                    int ticketCount = ticketEntityMapper.selectAllActiveTickets().size();
                    result.put("databaseConnection", "正常");
                    result.put("ticketCount", ticketCount);
                    LOGGER.info("数据库连接测试成功，票券数量: {}", ticketCount);
                } catch (Exception e) {
                    result.put("databaseConnection", "异常: " + e.getMessage());
                    LOGGER.error("数据库连接测试失败: {}", e.getMessage(), e);
                }
            } else {
                result.put("databaseConnection", "跳过（依赖未完全注入）");
            }
            
            // 检查Redis连接
            if (ticketCacheManagerInjected) {
                try {
                    boolean redisConnected = ticketCacheManager.isRedisConnected();
                    result.put("redisConnection", redisConnected ? "正常" : "异常");
                    LOGGER.info("Redis连接测试结果: {}", redisConnected ? "正常" : "异常");
                } catch (Exception e) {
                    result.put("redisConnection", "异常: " + e.getMessage());
                    LOGGER.error("Redis连接测试失败: {}", e.getMessage(), e);
                }
            } else {
                result.put("redisConnection", "跳过（依赖未注入）");
            }
            
            result.put("status", "SUCCESS");
            result.put("message", "依赖注入测试完成");
            result.put("timestamp", System.currentTimeMillis());
            
        } catch (Exception e) {
            LOGGER.error("依赖注入测试异常: {}", e.getMessage(), e);
            result.put("status", "ERROR");
            result.put("message", "依赖注入测试异常: " + e.getMessage());
            result.put("timestamp", System.currentTimeMillis());
        }
        
        return result;
    }
    
    /**
     * 测试数据库连接
     * @return 数据库连接测试结果
     */
    @GetMapping("/database")
    public Map<String, Object> testDatabase() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            if (ticketEntityMapper == null) {
                result.put("status", "ERROR");
                result.put("message", "TicketEntityMapper 未注入");
                return result;
            }
            
            // 测试数据库连接
            int ticketCount = ticketEntityMapper.selectAllActiveTickets().size();
            
            result.put("status", "SUCCESS");
            result.put("message", "数据库连接测试成功");
            result.put("ticketCount", ticketCount);
            result.put("timestamp", System.currentTimeMillis());
            
            LOGGER.info("数据库连接测试成功，票券数量: {}", ticketCount);
            
        } catch (Exception e) {
            LOGGER.error("数据库连接测试失败: {}", e.getMessage(), e);
            result.put("status", "ERROR");
            result.put("message", "数据库连接测试失败: " + e.getMessage());
            result.put("timestamp", System.currentTimeMillis());
        }
        
        return result;
    }
    
    /**
     * 测试Redis连接
     * @return Redis连接测试结果
     */
    @GetMapping("/redis")
    public Map<String, Object> testRedis() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            if (ticketCacheManager == null) {
                result.put("status", "ERROR");
                result.put("message", "TicketCacheManager 未注入");
                return result;
            }
            
            // 测试Redis连接
            boolean redisConnected = ticketCacheManager.isRedisConnected();
            
            result.put("status", "SUCCESS");
            result.put("message", "Redis连接测试完成");
            result.put("redisConnected", redisConnected);
            result.put("timestamp", System.currentTimeMillis());
            
            LOGGER.info("Redis连接测试完成，连接状态: {}", redisConnected ? "正常" : "异常");
            
        } catch (Exception e) {
            LOGGER.error("Redis连接测试失败: {}", e.getMessage(), e);
            result.put("status", "ERROR");
            result.put("message", "Redis连接测试失败: " + e.getMessage());
            result.put("timestamp", System.currentTimeMillis());
        }
        
        return result;
    }
}
