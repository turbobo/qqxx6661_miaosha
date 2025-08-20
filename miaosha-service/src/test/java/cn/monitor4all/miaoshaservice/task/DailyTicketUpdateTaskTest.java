package cn.monitor4all.miaoshaservice.task;

import cn.monitor4all.miaoshadao.dao.TicketEntity;
import cn.monitor4all.miaoshadao.mapper.TicketEntityMapper;
import cn.monitor4all.miaoshaservice.config.ScheduleConfig;
import cn.monitor4all.miaoshaservice.service.TicketCacheManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Date;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 每日票券更新定时任务测试类
 */
@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
public class DailyTicketUpdateTaskTest {

    @Mock
    private TicketEntityMapper ticketEntityMapper;

    @Mock
    private TicketCacheManager ticketCacheManager;

    @Mock
    private ScheduleConfig scheduleConfig;

    @InjectMocks
    private DailyTicketUpdateTask dailyTicketUpdateTask;

    private String todayStr;
    private String tomorrowStr;
    private String dayAfterTomorrowStr;

    @BeforeEach
    void setUp() {
        // 设置测试日期
        LocalDate today = LocalDate.now();
        todayStr = today.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        tomorrowStr = today.plusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        dayAfterTomorrowStr = today.plusDays(2).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

        // 设置配置默认值
        when(scheduleConfig.getEnabled()).thenReturn(true);
        when(scheduleConfig.getDayAfterTomorrowTotalCount()).thenReturn(100);
        when(scheduleConfig.getDayAfterTomorrowRemainingCount()).thenReturn(100);
        when(scheduleConfig.getDayAfterTomorrowSoldCount()).thenReturn(0);
        when(scheduleConfig.getDayAfterTomorrowName()).thenReturn("dayAfterTomorrow");
    }

    @Test
    void testUpdateDailyTickets_Enabled() {
        // 准备测试数据
        TicketEntity todayTicket = createMockTicket(todayStr, "today", 1, 0, 1);
        TicketEntity dayAfterTomorrowTicket = createMockTicket(dayAfterTomorrowStr, "dayAfterTomorrow", 3, 2, 1);

        // 设置Mock行为
        when(ticketEntityMapper.selectByDate(todayStr)).thenReturn(todayTicket);
        when(ticketEntityMapper.selectByDate(dayAfterTomorrowStr)).thenReturn(dayAfterTomorrowTicket);
        when(ticketEntityMapper.deleteByDate(anyString())).thenReturn(1);
        when(ticketEntityMapper.updateByPrimaryKey(any(TicketEntity.class))).thenReturn(1);

        // 执行测试
        dailyTicketUpdateTask.manualUpdateDailyTickets();

        // 验证调用
        verify(ticketEntityMapper).selectByDate(todayStr);
        verify(ticketEntityMapper).deleteByDate(todayStr);
        verify(ticketEntityMapper).selectByDate(dayAfterTomorrowStr);
        verify(ticketEntityMapper).updateByPrimaryKey(any(TicketEntity.class));
        verify(ticketCacheManager).deleteTicket(todayStr);
        verify(ticketCacheManager).deleteTicket(tomorrowStr);
        verify(ticketCacheManager).deleteTicket(dayAfterTomorrowStr);
    }

    @Test
    void testUpdateDailyTickets_Disabled() {
        // 设置任务禁用
        when(scheduleConfig.getEnabled()).thenReturn(false);

        // 执行测试
        dailyTicketUpdateTask.manualUpdateDailyTickets();

        // 验证没有调用任何数据库操作
        verify(ticketEntityMapper, never()).selectByDate(anyString());
        verify(ticketEntityMapper, never()).deleteByDate(anyString());
        verify(ticketEntityMapper, never()).updateByPrimaryKey(any(TicketEntity.class));
    }

    @Test
    void testUpdateDailyTickets_CreateNewTicket() {
        // 准备测试数据 - 后天没有票券记录
        TicketEntity todayTicket = createMockTicket(todayStr, "today", 1, 0, 1);

        // 设置Mock行为
        when(ticketEntityMapper.selectByDate(todayStr)).thenReturn(todayTicket);
        when(ticketEntityMapper.selectByDate(dayAfterTomorrowStr)).thenReturn(null);
        when(ticketEntityMapper.deleteByDate(anyString())).thenReturn(1);
        when(ticketEntityMapper.insert(any(TicketEntity.class))).thenReturn(1);

        // 执行测试
        dailyTicketUpdateTask.manualUpdateDailyTickets();

        // 验证调用
        verify(ticketEntityMapper).selectByDate(dayAfterTomorrowStr);
        verify(ticketEntityMapper).insert(any(TicketEntity.class));
    }

    @Test
    void testUpdateDailyTickets_NoTodayTicket() {
        // 准备测试数据 - 今天没有票券记录
        TicketEntity dayAfterTomorrowTicket = createMockTicket(dayAfterTomorrowStr, "dayAfterTomorrow", 3, 2, 1);

        // 设置Mock行为
        when(ticketEntityMapper.selectByDate(todayStr)).thenReturn(null);
        when(ticketEntityMapper.selectByDate(dayAfterTomorrowStr)).thenReturn(dayAfterTomorrowTicket);
        when(ticketEntityMapper.updateByPrimaryKey(any(TicketEntity.class))).thenReturn(1);

        // 执行测试
        dailyTicketUpdateTask.manualUpdateDailyTickets();

        // 验证调用
        verify(ticketEntityMapper).selectByDate(todayStr);
        verify(ticketEntityMapper, never()).deleteByDate(todayStr);
        verify(ticketEntityMapper).selectByDate(dayAfterTomorrowStr);
        verify(ticketEntityMapper).updateByPrimaryKey(any(TicketEntity.class));
    }

    /**
     * 创建模拟的票券实体
     */
    private TicketEntity createMockTicket(String date, String name, int totalCount, int remainingCount, int soldCount) {
        TicketEntity ticket = new TicketEntity();
        ticket.setId(1);
        ticket.setDate(date);
        ticket.setName(name);
        ticket.setTotalCount(totalCount);
        ticket.setRemainingCount(remainingCount);
        ticket.setSoldCount(soldCount);
        ticket.setVersion(2);
        ticket.setStatus(1);
        ticket.setCreateTime(new Date());
        ticket.setUpdateTime(new Date());
        return ticket;
    }
}
