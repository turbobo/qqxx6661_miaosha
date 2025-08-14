package cn.monitor4all.miaoshaweb.controller;

import cn.monitor4all.miaoshaservice.service.StockService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.annotation.Resource;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Controller
@CrossOrigin // 添加跨域支持
public class StockController {

    private static final Logger LOGGER = LoggerFactory.getLogger(OrderController.class);

    @Resource
    private StockService stockService;

    /**
     * 查询库存：通过数据库查询库存
     * @param sid
     * @return
     */
    @RequestMapping("/getStockByDB/{sid}")
    @ResponseBody
    public String getStockByDB(@PathVariable int sid) {
        int count;
        try {
            count = stockService.getStockCountByDB(sid);
        } catch (Exception e) {
            LOGGER.error("查询库存失败：[{}]", e.getMessage());
            return "查询库存失败";
        }
        LOGGER.info("商品Id: [{}] 剩余库存为: [{}]", sid, count);
        return String.format("商品Id: %d 剩余库存为：%d", sid, count);
    }

    /**
     * 查询库存：通过缓存查询库存
     * 缓存命中：返回库存
     * 缓存未命中：查询数据库写入缓存并返回
     * @param sid
     * @return
     */
    @RequestMapping("/getStockByCache/{sid}")
    @ResponseBody
    public String getStockByCache(@PathVariable int sid) {
        Integer count;
        try {
            count = stockService.getStockCount(sid);
        } catch (Exception e) {
            LOGGER.error("查询库存失败：[{}]", e.getMessage());
            return "查询库存失败";
        }
        LOGGER.info("商品Id: [{}] 剩余库存为: [{}]", sid, count);
        return String.format("商品Id: %d 剩余库存为：%d", sid, count);
    }

    /**
     * 管理员修改余票数量---乐观锁实现
     * @param sid 商品ID
     * @param count 新的余票数量
     *
     * 暂停秒杀活动后修改
     * 操作流程：
     * 管理员在系统后台点击 “暂停秒杀” 按钮，前端页面向用户展示 “秒杀活动正在维护中” 的提示，同时系统关闭秒杀下单接口，拒绝新的秒杀请求进入。
     * 进入库存修改界面，系统通过数据库事务锁定库存相关表，确保修改过程中没有其他操作对库存数据进行干扰。管理员输入需要修改的商品 ID、新的库存数量等信息后提交修改请求。
     * 系统完成库存数据更新后，释放数据库锁，点击 “恢复秒杀” 按钮，重新开放秒杀下单接口，前端页面提示用户秒杀活动继续进行。
     * 优势：能最大程度保证库存修改过程的安全性，避免与用户秒杀操作产生冲突，数据一致性容易保障。
     * 不足：会中断秒杀活动，影响用户体验，不适用于对秒杀连续性要求极高的场景。
     *
     * 基于乐观锁的无感知修改
     * 操作流程：
     * 管理员在系统后台输入要修改的商品库存信息。系统首先从数据库中查询当前库存数据以及对应的版本号。
     * 执行库存更新操作，使用类似UPDATE seckill_stock SET stock = #{newStock}, version = version + 1 WHERE goods_id = #{goodsId} AND version = #{currentVersion}的 SQL 语句。如果在更新过程中，其他用户的秒杀操作已经更新了版本号，导致当前更新条件不成立（影响行数为 0），则重新查询最新库存和版本号，重复更新操作，直到成功。
     * 整个过程中，秒杀业务正常运行，用户无感知。
     * 优势：不中断秒杀活动，对用户体验影响小。
     * 不足：如果并发冲突频繁，可能导致管理员的库存修改操作多次重试，增加系统开销；而且在极端情况下，可能存在一定时间内库存数据不一致的情况。
     * @return
     */
    @RequestMapping("/admin/updateStock")
    @ResponseBody
    public String adminUpdateStock(@RequestParam int sid, @RequestParam int count) {
        try {
            // 调用service层的管理员更新方法
            boolean success = stockService.adminUpdateStock(sid, count);
            if (success) {
                LOGGER.info("管理员修改商品Id: [{}] 库存为: [{}]成功", sid, count);
                return String.format("管理员修改商品Id: %d 库存更新为：%d", sid, count);
            } else {
                return "更新库存失败，请重试";
            }
        } catch (Exception e) {
            LOGGER.error("管理员修改库存失败：[{}]", e.getMessage());
            return "管理员修改库存失败：" + e.getMessage();
        }
    }

    /**
     * 管理员修改余票数量（悲观锁+事务控制）
     * @param sid 商品ID
     * @param count 新的余票数量
     * @return
     */
    @RequestMapping("/admin/updateStockWithPessimistic")
    @ResponseBody
    public String adminUpdateStockWithPessimistic(@RequestParam int sid, @RequestParam int count) {
        try {
            // 调用service层的管理员悲观锁更新方法
            boolean success = stockService.adminUpdateStockWithPessimistic(sid, count);
            if (success) {
                LOGGER.info("管理员悲观锁修改商品Id: [{}] 库存为: [{}]成功", sid, count);
                return String.format("管理员悲观锁修改商品Id: %d 库存更新为：%d", sid, count);
            } else {
                return "悲观锁更新库存失败，请重试";
            }
        } catch (Exception e) {
            LOGGER.error("管理员悲观锁修改库存失败：[{}]", e.getMessage());
            return "管理员悲观锁修改库存失败：" + e.getMessage();
        }
    }
}