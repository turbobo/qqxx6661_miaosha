package cn.monitor4all.miaoshaservice.task;

import cn.monitor4all.miaoshaservice.config.CacheConfig;
import cn.monitor4all.miaoshaservice.service.TicketCacheManager;
import cn.monitor4all.miaoshaservice.utils.CacheDeleteMessageUtil;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Resource;

/**
 * 缓存再删除线程
 */
@Slf4j
public class DelCacheByThread implements Runnable {
    private int sid;

    private String date;

    public DelCacheByThread(String date) {
        this.date = date;
    }


    @Resource
    private CacheConfig cacheConfig;

    @Resource
    private TicketCacheManager ticketCacheManager;

    @Resource
    private CacheDeleteMessageUtil cacheDeleteMessageUtil;

    public void run() {
        try {
            try {
                long delayMillis = cacheConfig.getDelayedDeleteDelay();
                log.info("异步执行缓存再删除，票券日期：[{}]， 首先休眠：[{}] 毫秒", date, delayMillis);
                Thread.sleep(delayMillis);

                // 第二次删除缓存
                ticketCacheManager.deleteTicket(date);
                log.debug("延迟删除缓存成功，日期: {}, 延迟时间: {}ms", date, delayMillis);

            } catch (Exception e) {
                log.warn("延迟删除缓存失败，日期: {}, 错误: {}", date, e.getMessage());

                // 延迟删除失败时，发送消息队列进行补偿删除
                cacheDeleteMessageUtil.sendTicketCacheDeleteMessage(date, "延迟删除失败后的补偿删除");
            }
            log.info("再次删除票券日期：[{}] 缓存", date);
        } catch (Exception e) {
            log.error("delCacheByThread执行出错", e);

            // 延迟删除失败时，发送消息队列进行补偿删除
            cacheDeleteMessageUtil.sendTicketCacheDeleteMessage(date, "延迟删除失败后的补偿删除");
        }
    }
}