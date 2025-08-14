package cn.monitor4all.miaoshaservice.service;

import cn.monitor4all.miaoshadao.dao.OrderRecord;
import cn.monitor4all.miaoshadao.dao.Stock;

public interface StockService {

    /**
     * 查询库存：通过缓存查询库存
     * 缓存命中：返回库存
     * 缓存未命中：查询数据库写入缓存并返回
     * @param id
     * @return
     */
    Integer getStockCount(int id);

    /**
     * 获取剩余库存：查数据库
     * @param id
     * @return
     */
    int getStockCountByDB(int id);

    /**
     * 获取剩余库存: 查缓存
     * @param id
     * @return
     */
    Integer getStockCountByCache(int id);

    /**
     * 将库存插入缓存
     * @param id
     * @return
     */
    void setStockCountCache(int id, int count);

    /**
     * 删除库存缓存
     * @param id
     */
    void delStockCountCache(int id);

    /**
     * 根据库存 ID 查询数据库库存信息
     * @param id
     * @return
     */
    Stock getStockById(int id);

    /**
     * 根据库存 ID 查询数据库库存信息（悲观锁）
     * @param id
     * @return
     */
    Stock getStockByIdForUpdate(int id);

    /**
     * 更新数据库库存信息
     * @param stock
     * return
     */
    int updateStockById(Stock stock);

    /**
     * 更新数据库库存信息（乐观锁）
     * @param stock
     * @return
     */
    public int updateStockByOptimistic(Stock stock);

    /**
     * 管理员更新库存
     * @param sid 商品ID
     * @param count 新的库存数量
     * @return 是否更新成功
     */
    boolean adminUpdateStock(int sid, int count);

    /**
     * 管理员更新库存（悲观锁+事务控制）
     * @param sid 商品ID
     * @param count 新的库存数量
     * @return 是否更新成功
     */
    boolean adminUpdateStockWithPessimistic(int sid, int count);

}
