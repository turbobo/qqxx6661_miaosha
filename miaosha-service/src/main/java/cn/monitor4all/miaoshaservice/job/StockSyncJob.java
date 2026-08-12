package cn.monitor4all.miaoshaservice.job;

import cn.monitor4all.miaoshadao.dao.TicketEntity;
import cn.monitor4all.miaoshadao.mapper.TicketEntityMapper;
import cn.monitor4all.miaoshaservice.service.StockRedisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 库存同步定时任务
 * 每日凌晨从 DB 同步最近3天的票券库存到 Redis，保证 Redis 库存数据与 DB 一致
 */
@Component
public class StockSyncJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(StockSyncJob.class);

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Resource
    private TicketEntityMapper ticketEntityMapper;

    @Resource
    private StockRedisService stockRedisService;

    /**
     * 每日凌晨 00:00 执行，同步最近3天（今天、明天、后天）的库存到 Redis
     */
    @Scheduled(cron = "0 0 0 * * ?")
    public void syncStockToRedis() {
        LOGGER.info("库存同步定时任务开始执行");
        try {
            LocalDate today = LocalDate.now();
            String startDate = today.format(DATE_FORMATTER);
            String endDate = today.plusDays(2).format(DATE_FORMATTER);

            List<TicketEntity> tickets = ticketEntityMapper.selectRecentTickets(startDate, endDate);
            if (tickets == null || tickets.isEmpty()) {
                LOGGER.info("最近3天无票券数据，跳过库存同步");
                return;
            }

            int successCount = 0;
            for (TicketEntity ticket : tickets) {
                try {
                    stockRedisService.syncStockFromDb(ticket.getDate(), ticket.getRemainingCount());
                    successCount++;
                } catch (Exception e) {
                    LOGGER.error("同步库存失败，日期: {}, 错误: {}", ticket.getDate(), e.getMessage(), e);
                }
            }

            LOGGER.info("库存同步定时任务执行完成，共同步 {}/{} 条记录", successCount, tickets.size());
        } catch (Exception e) {
            LOGGER.error("库存同步定时任务执行异常: {}", e.getMessage(), e);
        }
    }
}
