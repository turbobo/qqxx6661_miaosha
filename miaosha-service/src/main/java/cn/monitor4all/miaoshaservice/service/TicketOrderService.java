package cn.monitor4all.miaoshaservice.service;

import cn.monitor4all.miaoshadao.dao.TicketOrder;

import java.util.List;

/**
 * 票券订单服务接口
 */
public interface TicketOrderService {
    
    /**
     * 创建票券订单
     *
     * @param userId 用户ID
     * @param ticketId 票券ID
     * @param ticketCode 票券编码
     * @param ticketDate 购票日期
     * @param amount 订单金额（分）
     * @return 票券订单
     */
    TicketOrder createTicketOrder(Long userId, Integer ticketId, String ticketCode, String ticketDate, Long amount);
    
    /**
     * 根据主键查询票券订单
     *
     * @param id 主键ID
     * @return 票券订单
     */
    TicketOrder getTicketOrderById(Integer id);
    
    /**
     * 根据订单编号查询票券订单
     *
     * @param orderNo 订单编号
     * @return 票券订单
     */
    TicketOrder getTicketOrderByOrderNo(String orderNo);
    
    /**
     * 根据用户ID查询票券订单列表
     *
     * @param userId 用户ID
     * @return 票券订单列表
     */
    List<TicketOrder> getTicketOrdersByUserId(Long userId);
    
    /**
     * 根据票券编码查询票券订单
     *
     * @param ticketCode 票券编码
     * @return 票券订单
     */
    TicketOrder getTicketOrderByTicketCode(String ticketCode);
    
    /**
     * 根据票券ID查询票券订单列表
     *
     * @param ticketId 票券ID
     * @return 票券订单列表
     */
    List<TicketOrder> getTicketOrdersByTicketId(Integer ticketId);
    
    /**
     * 根据状态查询票券订单列表
     *
     * @param status 订单状态
     * @return 票券订单列表
     */
    List<TicketOrder> getTicketOrdersByStatus(Integer status);
    
    /**
     * 根据用户ID和状态查询票券订单列表
     *
     * @param userId 用户ID
     * @param status 订单状态
     * @return 票券订单列表
     */
    List<TicketOrder> getTicketOrdersByUserIdAndStatus(Long userId, Integer status);
    
    /**
     * 更新票券订单状态
     *
     * @param orderNo 订单编号
     * @param status 新状态
     * @return 是否更新成功
     */
    boolean updateTicketOrderStatus(String orderNo, Integer status);
    
    /**
     * 支付票券订单
     *
     * @param orderNo 订单编号
     * @return 是否支付成功
     */
    boolean payTicketOrder(String orderNo);
    
    /**
     * 取消票券订单
     *
     * @param orderNo 订单编号
     * @return 是否取消成功
     */
    boolean cancelTicketOrder(String orderNo);
    
    /**
     * 根据主键删除票券订单
     *
     * @param id 主键ID
     * @return 是否删除成功
     */
    boolean deleteTicketOrderById(Integer id);
    
    /**
     * 根据订单编号删除票券订单
     *
     * @param orderNo 订单编号
     * @return 是否删除成功
     */
    boolean deleteTicketOrderByOrderNo(String orderNo);
}
