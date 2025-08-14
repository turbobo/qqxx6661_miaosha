package cn.monitor4all.miaoshaservice.task;

import cn.monitor4all.miaoshadao.dao.OrderRecord;
import cn.monitor4all.miaoshaservice.constant.OrderRecordStatus;
import cn.monitor4all.miaoshaservice.service.OrderService;
import cn.monitor4all.miaoshaservice.service.TicketService;
import com.google.common.collect.Lists;
import com.mysql.jdbc.log.Log;
import lombok.extern.slf4j.Slf4j;
import javax.annotation.Resource;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @Author Jusven
 * @Date 2024/3/28 11:37
 */
// TODO 定时任务
@Slf4j
@EnableScheduling//开启定时任务
@Component
public class OneMinuteTask {
    @Resource
    private OrderService orderService;
    
    @Resource
    private TicketService ticketService;

    // 一分钟执行一次
    @Scheduled(cron="0/1 * * * * ?")//每秒钟执行一次，以空格分隔
    public void oneMinute() {
        // 遍历order_message表，代下发状态，重试3次--重试次数字段小于3，还是失败，则去发消息通知通知退回票数（数据库记录+mq）
        OrderRecord orderRecord = new OrderRecord();
        orderRecord.setStatus(OrderRecordStatus.NOT_SEND.getStatus());
        List<OrderRecord> orderRecords = orderService.selectOrderRecordNotSend(orderRecord);
        if (!CollectionUtils.isEmpty(orderRecords)) {

        }



        // 库存回退也是有数据库+mq双重保障，删除 用户-商品缓存
    }
    
    // 每天0点更新票券数据
    @Scheduled(cron = "0 0 0 * * ?")
    public void updateDailyTickets() {
        try {
            log.info("开始执行每日票券更新任务");
            // 调用票券服务更新每日票券
            if (ticketService instanceof cn.monitor4all.miaoshaservice.service.impl.TicketServiceImpl) {
                ((cn.monitor4all.miaoshaservice.service.impl.TicketServiceImpl) ticketService).updateDailyTickets();
                log.info("每日票券更新任务执行完成");
            }
        } catch (Exception e) {
            log.error("每日票券更新任务执行失败: {}", e.getMessage(), e);
        }
    }

    //  多线程 异步 重新下发
    //   根据订单id重新下发
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void tryOrder(List<OrderRecord> orderRecords) {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(10, 20, 3, TimeUnit.SECONDS, new LinkedBlockingQueue<>(100));


//        orderRecords 分批次，100个一组
        List<List<OrderRecord>> partition = Lists.partition(orderRecords, 1000);
        for (List<OrderRecord> list : partition) {
            // 异步重新创建订单
            executor.execute(() -> {
                for (OrderRecord record : list) {
                    try {
                        // 先修改重试次数
                        record.setCount(record.getCount()+1);
                        orderService.updateOrderRecordCount(record);

                        // 重新创建订单
                        orderService.createOrderByMq(record.getOrderId(), record.getSid(), record.getUserId());
                    } catch (Exception e) {
                        e.printStackTrace();
                        log.error("创建订单 重试失败");
                    }

                }
            });
        }


    }
}
