package cn.monitor4all.miaoshaservice.service;

import cn.monitor4all.miaoshadao.model.UpdateTicketsRequest;
import java.util.List;
import java.util.Map;

/**
 * 票券乐观锁无感知修改服务
 * 用于在秒杀过程中不中断活动的情况下修改库存
 */
public interface TicketOptimisticUpdateService {
    
    /**
     * 使用乐观锁无感知修改票券库存
     * @param ticketUpdates 票券更新列表
     * @return 修改结果
     */
    Map<String, Object> updateTicketsWithOptimistic(List<UpdateTicketsRequest.TicketUpdate> ticketUpdates);
    
    /**
     * 使用乐观锁修改单个票券库存
     * @param date 日期
     * @param newTotalCount 新的总票数
     * @param newRemainingCount 新的剩余票数
     * @param maxRetries 最大重试次数
     * @return 修改结果
     */
    Map<String, Object> updateSingleTicketWithOptimistic(String date, int newTotalCount, int newRemainingCount, int maxRetries);
    
    /**
     * 获取票券修改重试统计信息
     * @return 重试统计信息
     */
    Map<String, Object> getRetryStatistics();
}
