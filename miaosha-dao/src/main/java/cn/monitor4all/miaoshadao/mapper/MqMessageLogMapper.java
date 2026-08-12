package cn.monitor4all.miaoshadao.mapper;

import cn.monitor4all.miaoshadao.entity.MqMessageLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

/**
 * MQ消息本地日志Mapper接口
 */
@Mapper
public interface MqMessageLogMapper {

    /**
     * 插入消息日志
     *
     * @param log 消息日志
     * @return 影响行数
     */
    int insert(MqMessageLog log);

    /**
     * 更新消息状态
     *
     * @param messageId 消息唯一ID
     * @param status    目标状态
     * @return 影响行数
     */
    int updateStatus(@Param("messageId") String messageId, @Param("status") int status);

    /**
     * 更新消息状态、重试次数和下次重试时间
     *
     * @param messageId   消息唯一ID
     * @param status      目标状态
     * @param retryCount  重试次数
     * @param nextRetryTime 下次重试时间
     * @return 影响行数
     */
    int updateStatusAndRetry(@Param("messageId") String messageId,
                             @Param("status") int status,
                             @Param("retryCount") int retryCount,
                             @Param("nextRetryTime") Date nextRetryTime);

    /**
     * 查询待重试的消息列表
     * 条件：status IN (0, -1) AND next_retry_time <= beforeTime AND retry_count < maxRetry
     *
     * @param beforeTime 截止时间
     * @param maxRetry   最大重试次数
     * @return 待重试消息列表
     */
    List<MqMessageLog> selectPendingMessages(@Param("beforeTime") Date beforeTime,
                                              @Param("maxRetry") int maxRetry);

    /**
     * 标记消息为已消费
     *
     * @param messageId 消息唯一ID
     * @return 影响行数
     */
    int markAsConsumed(@Param("messageId") String messageId);
}
