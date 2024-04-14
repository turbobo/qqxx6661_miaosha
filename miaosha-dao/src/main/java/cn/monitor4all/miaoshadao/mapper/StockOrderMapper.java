package cn.monitor4all.miaoshadao.mapper;

import java.util.List;

import cn.monitor4all.miaoshadao.dao.OrderRecord;
import cn.monitor4all.miaoshadao.dao.StockOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface StockOrderMapper {

    int deleteByPrimaryKey(Integer id);

    int insert(StockOrder record);

    int insertSelective(StockOrder record);

    StockOrder selectByPrimaryKey(Integer id);

    int updateByPrimaryKeySelective(StockOrder record);

    int updateByPrimaryKey(StockOrder record);

    /**
     * 新增订单记录
     *
     * @param orderRecord 订单记录
     * @return 结果
     */
    public int insertOrderRecord(OrderRecord orderRecord);

    /**
     * 查询订单记录列表
     *
     * @param orderRecord 订单记录
     * @return 订单记录集合
     */
    public List<OrderRecord> selectOrderRecordList(OrderRecord orderRecord);

    public List<OrderRecord> selectOrderRecordNotSend(OrderRecord orderRecord);

    public Integer updateOrderRecordStatus(OrderRecord orderRecord);

    public Integer updateOrderRecordCount(OrderRecord orderRecord);

}