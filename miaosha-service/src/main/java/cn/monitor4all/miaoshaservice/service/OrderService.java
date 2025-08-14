package cn.monitor4all.miaoshaservice.service;

import cn.monitor4all.miaoshadao.dao.OrderRecord;

import java.util.List;

public interface OrderService {

    /**
     * 创建错误订单
     * @param sid
     *  库存ID
     * @return
     *  订单ID
     */
    public int createWrongOrder(int sid);


    /**
     * 创建正确订单：下单乐观锁
     * @param sid
     * @return
     * @throws Exception
     */
    public int createOptimisticOrder(int sid);

    public int createOptimisticOrder(int sid, Long userId);


    /**
     * 创建正确订单：下单悲观锁 for update
     * @param sid
     * @return
     * @throws Exception
     */
    public int createPessimisticOrder(int sid);



    /**
     * 创建正确订单：验证库存 + 用户 + 时间 合法性 + 下单乐观锁
     * @param sid
     * @param userId
     * @param verifyHash
     * @return
     * @throws Exception
     */
    public int createVerifiedOrder(Integer sid, Long userId, String verifyHash) throws Exception;

    /**
     * 创建正确订单：验证库存 + 下单乐观锁 + 更新订单信息到缓存
     * @param sid
     * @param userId
     * @throws Exception
     */
    public void createOrderByMq(Integer sid, Long userId) throws Exception;

    public void createOrderByMq(String orderId, Integer sid, Long userId) throws Exception;

    /**
     * 检查缓存中用户是否已经有订单
     * @param sid
     * @param userId
     * @return
     * @throws Exception
     */
    public Boolean checkUserOrderInfoInCache(Integer sid, Long userId) throws Exception;


    // 写入订单记录表
    public void insertOrderRecord(OrderRecord orderRecord);

    public List<OrderRecord> selectOrderRecordList(OrderRecord orderRecord);

    public List<OrderRecord> selectOrderRecordNotSend(OrderRecord orderRecord);


    /**
     * 修改订单记录  根据orderId修改状态和时间
     *
     * @param orderRecord 订单记录
     * @return 结果
     */
    public Integer updateOrderRecordStatus(OrderRecord orderRecord);

    public Integer updateOrderRecordCount(OrderRecord orderRecord);
}
