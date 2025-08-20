package cn.monitor4all.miaoshadao.mapper;

import cn.monitor4all.miaoshadao.dao.TicketEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 票券数据库操作Mapper接口
 */
@Mapper
public interface TicketEntityMapper {
    
    /**
     * 根据主键查询票券
     * @param id 主键ID
     * @return 票券信息
     */
    TicketEntity selectByPrimaryKey(Integer id);
    
    /**
     * 根据日期查询票券
     * @param date 日期
     * @return 票券信息
     */
    TicketEntity selectByDate(String date);
    
    /**
     * 根据日期查询票券（悲观锁）
     * @param date 日期
     * @return 票券信息
     */
    TicketEntity selectByDateForUpdate(String date);
    
    /**
     * 查询指定日期范围内的票券
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @return 票券列表
     */
    List<TicketEntity> selectRecentTickets(@Param("startDate") String startDate, @Param("endDate") String endDate);
    
    /**
     * 查询所有有效的票券
     * @return 票券列表
     */
    List<TicketEntity> selectAllActiveTickets();
    
    /**
     * 插入票券
     * @param record 票券信息
     * @return 影响行数
     */
    int insert(TicketEntity record);
    
    /**
     * 选择性插入票券
     * @param record 票券信息
     * @return 影响行数
     */
    int insertSelective(TicketEntity record);
    
    /**
     * 根据主键更新票券
     * @param record 票券信息
     * @return 影响行数
     */
    int updateByPrimaryKey(TicketEntity record);
    
    /**
     * 选择性更新票券
     * @param record 票券信息
     * @return 影响行数
     */
    int updateByPrimaryKeySelective(TicketEntity record);
    
    /**
     * 乐观锁更新票券库存
     * @param record 票券信息
     * @return 影响行数
     */
    int updateStockByOptimistic(TicketEntity record);
    
    /**
     * 根据主键删除票券
     * @param id 主键ID
     * @return 影响行数
     */
    int deleteByPrimaryKey(Integer id);
    
    /**
     * 根据日期删除票券
     * @param date 日期
     * @return 影响行数
     */
    int deleteByDate(String date);
} 