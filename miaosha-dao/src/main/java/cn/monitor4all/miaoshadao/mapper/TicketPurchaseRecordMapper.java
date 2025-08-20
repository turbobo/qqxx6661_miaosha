package cn.monitor4all.miaoshadao.mapper;

import cn.monitor4all.miaoshadao.dao.TicketPurchaseRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 票券购买记录数据库操作Mapper接口
 */
@Mapper
public interface TicketPurchaseRecordMapper {
    
    /**
     * 根据主键查询购买记录
     * @param id 主键ID
     * @return 购买记录
     */
    TicketPurchaseRecord selectByPrimaryKey(Integer id);
    
    /**
     * 根据订单ID查询购买记录
     * @param orderId 订单ID
     * @return 购买记录
     */
    TicketPurchaseRecord selectByOrderId(String orderId);
    
    /**
     * 根据票券编号查询购买记录
     * @param ticketCode 票券编号
     * @return 购买记录
     */
    TicketPurchaseRecord selectByTicketCode(String ticketCode);
    
    /**
     * 根据用户ID查询购买记录
     * @param userId 用户ID
     * @return 购买记录列表
     */
    List<TicketPurchaseRecord> selectByUserId(Long userId);
    
    /**
     * 根据用户ID和日期查询购买记录
     * @param userId 用户ID
     * @param date 日期
     * @return 购买记录
     */
    TicketPurchaseRecord selectByUserIdAndDate(@Param("userId") Long userId, @Param("date") String date);
    
    /**
     * 根据票券ID查询购买记录
     * @param ticketId 票券ID
     * @return 购买记录列表
     */
    List<TicketPurchaseRecord> selectByTicketId(Integer ticketId);
    
    /**
     * 根据日期查询购买记录
     * @param date 日期
     * @return 购买记录列表
     */
    List<TicketPurchaseRecord> selectByDate(String date);
    
    /**
     * 查询所有购买记录
     * @return 购买记录列表
     */
    List<TicketPurchaseRecord> selectAll();
    
    /**
     * 插入购买记录
     * @param record 购买记录
     * @return 影响行数
     */
    int insert(TicketPurchaseRecord record);
    
    /**
     * 选择性插入购买记录
     * @param record 购买记录
     * @return 影响行数
     */
    int insertSelective(TicketPurchaseRecord record);
    
    /**
     * 根据主键更新购买记录
     * @param record 购买记录
     * @return 影响行数
     */
    int updateByPrimaryKey(TicketPurchaseRecord record);
    
    /**
     * 选择性更新购买记录
     * @param record 购买记录
     * @return 影响行数
     */
    int updateByPrimaryKeySelective(TicketPurchaseRecord record);
    
    /**
     * 根据主键删除购买记录
     * @param id 主键ID
     * @return 影响行数
     */
    int deleteByPrimaryKey(Integer id);
    
    /**
     * 根据订单ID删除购买记录
     * @param orderId 订单ID
     * @return 影响行数
     */
    int deleteByOrderId(String orderId);
    
    /**
     * 统计用户购买数量
     * @param userId 用户ID
     * @return 购买数量
     */
    int countByUserId(Long userId);
    
    /**
     * 统计指定日期的购买数量
     * @param date 日期
     * @return 购买数量
     */
    int countByDate(String date);
} 