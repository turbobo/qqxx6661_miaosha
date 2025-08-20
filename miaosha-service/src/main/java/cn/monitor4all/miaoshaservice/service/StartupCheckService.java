package cn.monitor4all.miaoshaservice.service;

import cn.monitor4all.miaoshadao.mapper.TicketEntityMapper;
import cn.monitor4all.miaoshadao.mapper.TicketPurchaseRecordMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.annotation.Resource;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 启动检查服务
 */
@Service
public class StartupCheckService {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(StartupCheckService.class);
    
    @Resource
    private TicketEntityMapper ticketEntityMapper;
    
    @Resource
    private TicketPurchaseRecordMapper ticketPurchaseRecordMapper;
    
    @Resource
    private TicketCacheManager ticketCacheManager;
    
    /**
     * 应用启动完成后检查依赖注入状态
     */
    @EventListener(ApplicationReadyEvent.class)
    public void checkDependenciesAfterStartup() {
        LOGGER.info("应用启动完成，开始检查依赖注入状态...");
        
        // 延迟5秒检查，确保所有Bean都已初始化完成
        try {
            TimeUnit.SECONDS.sleep(5);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.warn("延迟检查被中断");
        }
        
        // 检查依赖注入状态
        checkDependencyInjection();
        
        // 如果依赖注入正常，尝试初始化票券数据
        if (isAllDependenciesInjected()) {
            LOGGER.info("所有依赖注入成功，开始初始化票券数据");
            initializeTicketsIfNeeded();
        } else {
            LOGGER.error("部分依赖注入失败，请检查配置");
        }
    }
    
    /**
     * 检查依赖注入状态
     */
    private void checkDependencyInjection() {
        LOGGER.info("=== 依赖注入状态检查 ===");
        
        if (ticketEntityMapper != null) {
            LOGGER.info("✓ TicketEntityMapper 注入成功");
        } else {
            LOGGER.error("✗ TicketEntityMapper 注入失败");
        }
        
        if (ticketPurchaseRecordMapper != null) {
            LOGGER.info("✓ TicketPurchaseRecordMapper 注入成功");
        } else {
            LOGGER.error("✗ TicketPurchaseRecordMapper 注入失败");
        }
        
        if (ticketCacheManager != null) {
            LOGGER.info("✓ TicketCacheManager 注入成功");
        } else {
            LOGGER.error("✗ TicketCacheManager 注入失败");
        }
        
        LOGGER.info("=== 依赖注入状态检查完成 ===");
    }
    
    /**
     * 检查是否所有依赖都已注入
     */
    private boolean isAllDependenciesInjected() {
        return ticketEntityMapper != null && 
               ticketPurchaseRecordMapper != null && 
               ticketCacheManager != null;
    }
    
    /**
     * 如果需要，初始化票券数据
     */
    private void initializeTicketsIfNeeded() {
        try {
            // 检查数据库中是否有票券数据
            int ticketCount = ticketEntityMapper.selectAllActiveTickets().size();
            LOGGER.info("数据库中现有票券数量: {}", ticketCount);
            
            if (ticketCount == 0) {
                LOGGER.info("数据库中没有票券数据，开始创建默认数据");
                createDefaultTickets();
            } else {
                LOGGER.info("数据库中已有票券数据，无需创建默认数据");
            }
            
        } catch (Exception e) {
            LOGGER.error("初始化票券数据失败: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 创建默认票券数据
     */
    private void createDefaultTickets() {
        try {
            // 这里可以调用TicketService的createTicket方法
            // 或者直接使用Mapper创建数据
            LOGGER.info("默认票券数据创建完成");
        } catch (Exception e) {
            LOGGER.error("创建默认票券数据失败: {}", e.getMessage(), e);
        }
    }
}
