package cn.monitor4all.miaoshaservice.service.impl;

import cn.monitor4all.miaoshadao.dao.TicketEntity;
import cn.monitor4all.miaoshadao.mapper.TicketEntityMapper;
import cn.monitor4all.miaoshadao.model.UpdateTicketsRequest;
import cn.monitor4all.miaoshaservice.service.TicketCacheManager;
import cn.monitor4all.miaoshaservice.service.TicketOptimisticUpdateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class TicketOptimisticUpdateServiceImpl implements TicketOptimisticUpdateService {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(TicketOptimisticUpdateServiceImpl.class);
    
    @Resource
    private TicketEntityMapper ticketEntityMapper;
    
    @Resource
    private TicketCacheManager ticketCacheManager;
    
    // 重试统计信息
    private final Map<String, AtomicInteger> retryCountMap = new ConcurrentHashMap<>();
    private final AtomicInteger totalRetryCount = new AtomicInteger(0);
    private final AtomicInteger totalUpdateCount = new AtomicInteger(0);
    
    @Override
    @Transactional
    public Map<String, Object> updateTicketsWithOptimistic(List<UpdateTicketsRequest.TicketUpdate> ticketUpdates) {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> updateResults = new ArrayList<>();
        int successCount = 0;
        int failCount = 0;
        int totalRetries = 0;
        
        try {
            LOGGER.info("开始使用乐观锁无感知修改票数，共{}个更新请求", ticketUpdates.size());
            
            for (UpdateTicketsRequest.TicketUpdate update : ticketUpdates) {
                Map<String, Object> updateResult = new HashMap<>();
                updateResult.put("date", update.getDate());
                
                try {
                    // 使用乐观锁修改单个票券
                    Map<String, Object> singleResult = updateSingleTicketWithOptimistic(
                        update.getDate(), 
                        update.getTotalCount(), 
                        update.getRemainingCount(), 
                        5 // 最大重试5次
                    );
                    
                    if ("SUCCESS".equals(singleResult.get("status"))) {
                        updateResult.put("status", "SUCCESS");
                        updateResult.put("message", "票券更新成功");
                        updateResult.put("retryCount", singleResult.get("retryCount"));
                        updateResult.put("oldTotalCount", singleResult.get("oldTotalCount"));
                        updateResult.put("newTotalCount", singleResult.get("newTotalCount"));
                        updateResult.put("oldRemainingCount", singleResult.get("oldRemainingCount"));
                        updateResult.put("newRemainingCount", singleResult.get("newRemainingCount"));
                        successCount++;
                        totalRetries += (Integer) singleResult.get("retryCount");
                    } else {
                        updateResult.put("status", "FAILED");
                        updateResult.put("message", singleResult.get("message"));
                        failCount++;
                    }
                    
                } catch (Exception e) {
                    LOGGER.error("修改票券失败，日期: {}, 错误: {}", update.getDate(), e.getMessage(), e);
                    updateResult.put("status", "ERROR");
                    updateResult.put("message", "修改失败: " + e.getMessage());
                    failCount++;
                }
                
                updateResults.add(updateResult);
            }
            
            // 更新票券列表缓存
            try {
                ticketCacheManager.clearAllTicketCache();
            } catch (Exception e) {
                LOGGER.warn("更新票券列表缓存失败: {}", e.getMessage());
            }
            
            result.put("status", "SUCCESS");
            result.put("message", String.format("乐观锁修改完成，成功: %d, 失败: %d, 总重试次数: %d", 
                successCount, failCount, totalRetries));
            result.put("totalCount", ticketUpdates.size());
            result.put("successCount", successCount);
            result.put("failCount", failCount);
            result.put("totalRetries", totalRetries);
            result.put("updateResults", updateResults);
            result.put("timestamp", System.currentTimeMillis());
            
            LOGGER.info("乐观锁修改票数完成，成功: {}, 失败: {}, 总重试次数: {}", 
                successCount, failCount, totalRetries);
            
        } catch (Exception e) {
            LOGGER.error("乐观锁修改票数异常: {}", e.getMessage(), e);
            result.put("status", "ERROR");
            result.put("message", "乐观锁修改异常: " + e.getMessage());
            result.put("timestamp", System.currentTimeMillis());
        }
        
        return result;
    }
    
    @Override
    @Transactional
    public Map<String, Object> updateSingleTicketWithOptimistic(String date, int newTotalCount, 
                                                              int newRemainingCount, int maxRetries) {
        Map<String, Object> result = new HashMap<>();
        int retryCount = 0;
        
        try {
            while (retryCount < maxRetries) {
                // 查询当前票券信息
                TicketEntity ticketEntity = ticketEntityMapper.selectByDate(date);
                
                if (ticketEntity == null) {
                    // 如果票券不存在，创建新的票券
                    ticketEntity = new TicketEntity();
                    ticketEntity.setDate(date);
                    ticketEntity.setName("票券");
                    ticketEntity.setTotalCount(newTotalCount);
                    ticketEntity.setRemainingCount(newRemainingCount);
                    ticketEntity.setSoldCount(newTotalCount - newRemainingCount);
                    ticketEntity.setVersion(1);
                    ticketEntity.setStatus(1);
                    
                    int insertResult = ticketEntityMapper.insert(ticketEntity);
                    if (insertResult > 0) {
                        result.put("status", "SUCCESS");
                        result.put("message", "票券创建成功");
                        result.put("retryCount", retryCount);
                        result.put("oldTotalCount", 0);
                        result.put("newTotalCount", newTotalCount);
                        result.put("oldRemainingCount", 0);
                        result.put("newRemainingCount", newRemainingCount);
                        
                        // 更新统计信息
                        updateRetryStatistics(date, retryCount);
                        
                        // 清除缓存
                        ticketCacheManager.deleteTicket(date);
                        
                        LOGGER.info("票券创建成功，日期: {}, 总票数: {}, 剩余票数: {}, 重试次数: {}", 
                            date, newTotalCount, newRemainingCount, retryCount);
                        return result;
                    } else {
                        result.put("status", "FAILED");
                        result.put("message", "票券创建失败");
                        return result;
                    }
                }
                
                // 票券存在，使用乐观锁更新
                int oldTotalCount = ticketEntity.getTotalCount();
                int oldRemainingCount = ticketEntity.getRemainingCount();
                int currentVersion = ticketEntity.getVersion();
                
                // 计算新的已售数量
                int newSoldCount = Math.max(0, newTotalCount - newRemainingCount);
                
                // 更新票券信息
                ticketEntity.setTotalCount(newTotalCount);
                ticketEntity.setRemainingCount(newRemainingCount);
                ticketEntity.setSoldCount(newSoldCount);
                ticketEntity.setVersion(currentVersion + 1);
                
                // 使用乐观锁更新
                int updateResult = ticketEntityMapper.updateStockByOptimistic(ticketEntity);
                
                if (updateResult > 0) {
                    result.put("status", "SUCCESS");
                    result.put("message", "票券更新成功");
                    result.put("retryCount", retryCount);
                    result.put("oldTotalCount", oldTotalCount);
                    result.put("newTotalCount", newTotalCount);
                    result.put("oldRemainingCount", oldRemainingCount);
                    result.put("newRemainingCount", newRemainingCount);
                    
                    // 更新统计信息
                    updateRetryStatistics(date, retryCount);
                    
                    // 清除缓存
                    ticketCacheManager.deleteTicket(date);
                    
                    LOGGER.info("票券更新成功，日期: {}, 总票数: {}->{}, 剩余票数: {}->{}, 重试次数: {}", 
                        date, oldTotalCount, newTotalCount, oldRemainingCount, newRemainingCount, retryCount);
                    return result;
                } else {
                    // 乐观锁更新失败，版本号不匹配
                    retryCount++;
                    LOGGER.warn("乐观锁更新失败，日期: {}, 当前版本: {}, 重试次数: {}/{}", 
                        date, currentVersion, retryCount, maxRetries);
                    
                    // 短暂等待后重试
                    if (retryCount < maxRetries) {
                        try {
                            Thread.sleep(10); // 等待10毫秒
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            }
            
            // 达到最大重试次数
            result.put("status", "FAILED");
            result.put("message", String.format("达到最大重试次数: %d", maxRetries));
            result.put("retryCount", retryCount);
            
            LOGGER.error("票券更新失败，日期: {}, 达到最大重试次数: {}", date, maxRetries);
            
        } catch (Exception e) {
            LOGGER.error("票券更新异常，日期: {}, 错误: {}", date, e.getMessage(), e);
            result.put("status", "ERROR");
            result.put("message", "更新异常: " + e.getMessage());
            result.put("retryCount", retryCount);
        }
        
        return result;
    }
    
    @Override
    public Map<String, Object> getRetryStatistics() {
        Map<String, Object> statistics = new HashMap<>();
        
        // 计算总重试次数
        int totalRetries = totalRetryCount.get();
        int totalUpdates = totalUpdateCount.get();
        
        // 计算平均重试次数
        double avgRetries = totalUpdates > 0 ? (double) totalRetries / totalUpdates : 0;
        
        // 获取各票券的重试次数
        Map<String, Integer> ticketRetryCounts = new HashMap<>();
        for (Map.Entry<String, AtomicInteger> entry : retryCountMap.entrySet()) {
            ticketRetryCounts.put(entry.getKey(), entry.getValue().get());
        }
        
        statistics.put("totalRetryCount", totalRetries);
        statistics.put("totalUpdateCount", totalUpdates);
        statistics.put("averageRetryCount", Math.round(avgRetries * 100.0) / 100.0);
        statistics.put("ticketRetryCounts", ticketRetryCounts);
        statistics.put("timestamp", System.currentTimeMillis());
        
        return statistics;
    }
    
    /**
     * 更新重试统计信息
     */
    private void updateRetryStatistics(String date, int retryCount) {
        totalUpdateCount.incrementAndGet();
        totalRetryCount.addAndGet(retryCount);
        
        retryCountMap.computeIfAbsent(date, k -> new AtomicInteger(0))
                    .addAndGet(retryCount);
    }
}
