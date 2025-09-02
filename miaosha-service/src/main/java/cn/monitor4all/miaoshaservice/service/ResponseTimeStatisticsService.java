package cn.monitor4all.miaoshaservice.service;

import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 响应时间统计服务
 * 用于收集和计算接口响应时间的统计数据
 */
@Service
public class ResponseTimeStatisticsService {

    // 存储响应时间的队列
    private final ConcurrentLinkedQueue<Long> responseTimes = new ConcurrentLinkedQueue<>();
    
    // 总响应时间
    private final AtomicLong totalResponseTime = new AtomicLong(0);
    
    // 请求总数
    private final AtomicLong totalRequests = new AtomicLong(0);
    
    /**
     * 添加响应时间记录
     * @param responseTime 响应时间（毫秒）
     */
    public void addResponseTime(long responseTime) {
        responseTimes.offer(responseTime);
        totalResponseTime.addAndGet(responseTime);
        totalRequests.incrementAndGet();
    }
    
    /**
     * 获取平均响应时间
     * @return 平均响应时间（毫秒）
     */
    public double getAverageResponseTime() {
        long totalCount = totalRequests.get();
        if (totalCount == 0) {
            return 0.0;
        }
        return (double) totalResponseTime.get() / totalCount;
    }
    
    /**
     * 获取统计信息
     * @return 包含统计信息的字符串
     */
    public String getStatistics() {
        long totalCount = totalRequests.get();
        double averageTime = getAverageResponseTime();
        
        return String.format("总请求数: %d, 平均响应时间: %.2f ms", totalCount, averageTime);
    }
    
    /**
     * 清空统计数据
     */
    public void clearStatistics() {
        responseTimes.clear();
        totalResponseTime.set(0);
        totalRequests.set(0);
    }
    
    /**
     * 获取请求总数
     * @return 请求总数
     */
    public long getTotalRequests() {
        return totalRequests.get();
    }
}