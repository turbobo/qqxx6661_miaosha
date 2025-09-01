package cn.monitor4all.miaoshadao.mapper;

import cn.monitor4all.miaoshadao.dao.TicketOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 票券订单Mapper接口
 */
@Mapper
public interface TicketOrderMapper {
    
    /**
     * 插入票券订单
     *
     * @param ticketOrder 票券订单
     * @return 影响行数
     */
    int insert(TicketOrder ticketOrder);
    
    /**
     * 根据主键更新票券订单
     *
     * @param ticketOrder 票券订单
     * @return 影响行数
     */
    int updateByPrimaryKey(TicketOrder ticketOrder);
    
    /**
     * 根据主键查询票券订单
     *
     * @param id 主键ID
     * @return 票券订单
     */
    TicketOrder selectByPrimaryKey(Integer id);
    
    /**
     * 根据订单编号查询票券订单
     *
     * @param orderNo 订单编号
     * @return 票券订单
     */
    TicketOrder selectByOrderNo(String orderNo);
    
    /**
     * 根据用户ID查询票券订单列表
     *
     * @param userId 用户ID
     * @return 票券订单列表
     */
    List<TicketOrder> selectByUserId(Long userId);
    
    TicketOrder selectById(Long orderId);
    
    /**
     * 根据票券编码查询票券订单
     *
     * @param ticketCode 票券编码
     * @return 票券订单
     */
    TicketOrder selectByTicketCode(String ticketCode);
    
    /**
     * 根据票券ID查询票券订单列表
     *
     * @param ticketId 票券ID
     * @return 票券订单列表
     */
    List<TicketOrder> selectByTicketId(Integer ticketId);
    
    /**
     * 根据状态查询票券订单列表
     *
     * @param status 订单状态
     * @return 票券订单列表
     */
    List<TicketOrder> selectByStatus(Integer status);
    
    /**
     * 根据用户ID和状态查询票券订单列表
     *
     * @param userId 用户ID
     * @param status 订单状态
     * @return 票券订单列表
     */
    List<TicketOrder> selectByUserIdAndStatus(@Param("userId") Long userId, @Param("status") Integer status);
    
    /**
     * 根据用户ID和票券日期查询票券订单
     *
     * @param userId 用户ID
     * @param ticketDate 票券日期
     * @return 票券订单
     */
    TicketOrder selectByUserIdAndDate(@Param("userId") Long userId, @Param("date") String ticketDate);
    
    /**
     * 根据主键删除票券订单
     *
     * @param id 主键ID
     * @return 影响行数
     */
    int deleteByPrimaryKey(Integer id);
    
    /**
     * 根据订单编号删除票券订单
     *
     * @param orderNo 订单编号
     * @return 影响行数
     */
    int deleteByOrderNo(String orderNo);
}
