package cn.monitor4all.miaoshaservice.task;

import cn.monitor4all.miaoshadao.dao.TicketEntity;
import cn.monitor4all.miaoshadao.mapper.TicketEntityMapper;
import cn.monitor4all.miaoshaservice.config.ScheduleConfig;
import cn.monitor4all.miaoshaservice.service.TicketCacheManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Date;

/**
 * 每日票券数据更新定时任务
 * 每天凌晨1点执行：
 * 1. 清除今天的票数
 * 2. 保留明天、后天的票数
 * 3. 更新后天的票数
 */
@Slf4j
@EnableScheduling
@Component
public class DailyTicketUpdateTask {

    @Resource
    private TicketEntityMapper ticketEntityMapper;

    @Resource
    private TicketCacheManager ticketCacheManager;
    
    @Resource
    private ScheduleConfig scheduleConfig;

    /**
     * 每天凌晨1点执行票券数据更新任务
     * cron表达式：秒 分 时 日 月 周
     */
    @Scheduled(cron = "${schedule.ticket.daily-ticket-update-cron:0 0 1 * * ?}")
    @Transactional(rollbackFor = Exception.class)
    public void updateDailyTickets() {
        // 检查是否启用定时任务
        if (!scheduleConfig.getEnabled()) {
            log.info("每日票券数据更新任务已禁用");
            return;
        }
        try {
            log.info("开始执行每日票券数据更新任务");
            
            // 获取当前日期
            LocalDate today = LocalDate.now();
            String todayStr = today.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            String tomorrowStr = today.plusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            String dayAfterTomorrowStr = today.plusDays(2).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            
            log.info("更新日期范围：今天={}, 明天={}, 后天={}", todayStr, tomorrowStr, dayAfterTomorrowStr);
            
            // 1. 清除今天的票数（删除记录）
            clearTodayTickets(todayStr);
            
            // 2. 保留明天、后天的票数（不做修改）
            log.info("保留明天和后天的票数，不做修改");
            
            // 3. 更新后天的票数
            updateDayAfterTomorrowTickets(dayAfterTomorrowStr);
            
            // 4. 清理相关缓存
            cleanRelatedCache(todayStr, tomorrowStr, dayAfterTomorrowStr);
            
            log.info("每日票券数据更新任务执行完成");
            
        } catch (Exception e) {
            log.error("每日票券数据更新任务执行失败", e);
            throw e; // 重新抛出异常，触发事务回滚
        }
    }
    
    /**
     * 清除今天的票数
     * @param todayStr 今天的日期字符串
     */
    private void clearTodayTickets(String todayStr) {
        try {
            log.info("开始清除今天的票数，日期: {}", todayStr);
            
            // 查询今天的票券记录
            TicketEntity todayTicket = ticketEntityMapper.selectByDate(todayStr);
            if (todayTicket != null) {
                // 删除今天的票券记录
                int deleteResult = ticketEntityMapper.deleteByDate(todayStr);
                if (deleteResult > 0) {
                    log.info("成功删除今天的票券记录，日期: {}, 记录ID: {}", todayStr, todayTicket.getId());
                } else {
                    log.warn("删除今天的票券记录失败，日期: {}", todayStr);
                }
            } else {
                log.info("今天没有票券记录需要删除，日期: {}", todayStr);
            }
            
        } catch (Exception e) {
            log.error("清除今天的票数失败，日期: {}, 错误: {}", todayStr, e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * 更新后天的票数
     * @param dayAfterTomorrowStr 后天的日期字符串
     */
    private void updateDayAfterTomorrowTickets(String dayAfterTomorrowStr) {
        try {
            log.info("开始更新后天的票数，日期: {}", dayAfterTomorrowStr);
            
            // 查询后天的票券记录
            TicketEntity dayAfterTomorrowTicket = ticketEntityMapper.selectByDate(dayAfterTomorrowStr);
            
            if (dayAfterTomorrowTicket != null) {
                // 更新后天的票数信息
                updateDayAfterTomorrowTicketInfo(dayAfterTomorrowTicket);
            } else {
                // 如果后天没有票券记录，创建新的记录
                createDayAfterTomorrowTicket(dayAfterTomorrowStr);
            }
            
        } catch (Exception e) {
            log.error("更新后天的票数失败，日期: {}, 错误: {}", dayAfterTomorrowStr, e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * 更新后天票券的具体信息
     * @param ticket 后天票券实体
     */
    private void updateDayAfterTomorrowTicketInfo(TicketEntity ticket) {
        try {
            // 从配置中获取新的票数信息
            int newTotalCount = scheduleConfig.getDayAfterTomorrowTotalCount();
            int newRemainingCount = scheduleConfig.getDayAfterTomorrowRemainingCount();
            int newSoldCount = scheduleConfig.getDayAfterTomorrowSoldCount();
            String newName = scheduleConfig.getDayAfterTomorrowName();
            
            // 更新票券信息
            ticket.setTotalCount(newTotalCount);
            ticket.setRemainingCount(newRemainingCount);
            ticket.setSoldCount(newSoldCount);
            ticket.setName(newName);
            ticket.setVersion(ticket.getVersion() + 1);
            ticket.setUpdateTime(new Date());
            
            // 执行更新
            int updateResult = ticketEntityMapper.updateByPrimaryKey(ticket);
            if (updateResult > 0) {
                log.info("成功更新后天票券信息，日期: {}, 新总票数: {}, 新剩余票数: {}, 新已售票数: {}", 
                    ticket.getDate(), newTotalCount, newRemainingCount, newSoldCount);
            } else {
                log.warn("更新后天票券信息失败，日期: {}", ticket.getDate());
            }
            
        } catch (Exception e) {
            log.error("更新后天票券信息失败，日期: {}, 错误: {}", ticket.getDate(), e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * 创建后天的票券记录
     * @param dayAfterTomorrowStr 后天的日期字符串
     */
    private void createDayAfterTomorrowTicket(String dayAfterTomorrowStr) {
        try {
            log.info("开始创建后天的票券记录，日期: {}", dayAfterTomorrowStr);
            
            // 从配置中获取新的票数信息
            int newTotalCount = scheduleConfig.getDayAfterTomorrowTotalCount();
            int newRemainingCount = scheduleConfig.getDayAfterTomorrowRemainingCount();
            int newSoldCount = scheduleConfig.getDayAfterTomorrowSoldCount();
            String newName = scheduleConfig.getDayAfterTomorrowName();
            
            // 创建新的票券实体
            TicketEntity newTicket = new TicketEntity();
            newTicket.setDate(dayAfterTomorrowStr);
            newTicket.setName(newName);
            newTicket.setTotalCount(newTotalCount);
            newTicket.setRemainingCount(newRemainingCount);
            newTicket.setSoldCount(newSoldCount);
            newTicket.setVersion(1);
            newTicket.setStatus(1);
            newTicket.setCreateTime(new Date());
            newTicket.setUpdateTime(new Date());
            
            // 插入数据库
            int insertResult = ticketEntityMapper.insert(newTicket);
            if (insertResult > 0) {
                log.info("成功创建后天的票券记录，日期: {}, 总票数: {}, 剩余票数: {}", 
                    dayAfterTomorrowStr, newTicket.getTotalCount(), newTicket.getRemainingCount());
            } else {
                log.warn("创建后天的票券记录失败，日期: {}", dayAfterTomorrowStr);
            }
            
        } catch (Exception e) {
            log.error("创建后天的票券记录失败，日期: {}, 错误: {}", dayAfterTomorrowStr, e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * 清理相关缓存
     * @param todayStr 今天的日期字符串
     * @param tomorrowStr 明天的日期字符串
     * @param dayAfterTomorrowStr 后天的日期字符串
     */
    private void cleanRelatedCache(String todayStr, String tomorrowStr, String dayAfterTomorrowStr) {
        try {
            log.info("开始清理相关缓存");
            
            // 删除相关日期的缓存
            ticketCacheManager.deleteTicket(todayStr);
            ticketCacheManager.deleteTicket(tomorrowStr);
            ticketCacheManager.deleteTicket(dayAfterTomorrowStr);
            
            // 更新票券列表缓存
            try {
                // 获取最新的票券列表并更新缓存
                // 这里可以调用TicketService的相关方法
                log.info("缓存清理完成");
            } catch (Exception e) {
                log.warn("更新票券列表缓存失败: {}", e.getMessage());
            }
            
        } catch (Exception e) {
            log.error("清理相关缓存失败", e);
            // 缓存清理失败不影响主流程，只记录日志
        }
    }
    
    /**
     * 手动执行票券数据更新（用于测试或手动触发）
     */
    public void manualUpdateDailyTickets() {
        log.info("手动触发每日票券数据更新任务");
        updateDailyTickets();
    }
}
