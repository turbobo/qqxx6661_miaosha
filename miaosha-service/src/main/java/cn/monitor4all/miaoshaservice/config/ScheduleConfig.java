package cn.monitor4all.miaoshaservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 定时任务配置类
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "schedule.ticket")
public class ScheduleConfig {
    
    /**
     * 每日票券更新任务的cron表达式
     * 默认每天凌晨1点执行
     */
    private String dailyTicketUpdateCron = "0 0 1 * * ?";
    
    /**
     * 后天票券的新总票数
     */
    private Integer dayAfterTomorrowTotalCount = 100;
    
    /**
     * 后天票券的新剩余票数
     */
    private Integer dayAfterTomorrowRemainingCount = 100;
    
    /**
     * 后天票券的新已售票数
     */
    private Integer dayAfterTomorrowSoldCount = 0;
    
    /**
     * 后天票券的名称
     */
    private String dayAfterTomorrowName = "dayAfterTomorrow";
    
    /**
     * 是否启用每日票券更新任务
     */
    private Boolean enabled = true;
    
    /**
     * 任务执行超时时间（毫秒）
     */
    private Long timeout = 30000L;
}
