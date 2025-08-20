package cn.monitor4all.miaoshaservice.service.impl;

import cn.monitor4all.miaoshaservice.service.MiaoshaStatusService;
import cn.monitor4all.miaoshaservice.utils.RedisUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.concurrent.TimeUnit;

@Service
public class MiaoshaStatusServiceImpl implements MiaoshaStatusService {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(MiaoshaStatusServiceImpl.class);
    
    private static final String MIAOSHA_STATUS_KEY = "miaosha:status";
    private static final String MIAOSHA_PAUSE_TIME_KEY = "miaosha:pause_time";
    private static final String MIAOSHA_RESUME_TIME_KEY = "miaosha:resume_time";
    private static final String MIAOSHA_OPERATOR_KEY = "miaosha:operator";
    private static final String MIAOSHA_REASON_KEY = "miaosha:reason";
    
    @Resource
    private RedisUtil redisUtil;
    
    @Override
    public boolean pauseMiaosha() {
        try {
            long currentTime = System.currentTimeMillis();
            
            // 设置暂停状态
            redisUtil.set(MIAOSHA_STATUS_KEY, "PAUSED", 24, TimeUnit.HOURS);
            redisUtil.set(MIAOSHA_PAUSE_TIME_KEY, String.valueOf(currentTime), 24, TimeUnit.HOURS);
            redisUtil.set(MIAOSHA_OPERATOR_KEY, "admin", 24, TimeUnit.HOURS);
            redisUtil.set(MIAOSHA_REASON_KEY, "库存维护", 24, TimeUnit.HOURS);
            
            LOGGER.info("秒杀活动已暂停，时间: {}", currentTime);
            return true;
        } catch (Exception e) {
            LOGGER.error("暂停秒杀活动失败: {}", e.getMessage(), e);
            return false;
        }
    }
    
    @Override
    public boolean resumeMiaosha() {
        try {
            long currentTime = System.currentTimeMillis();
            
            // 设置恢复状态
            redisUtil.set(MIAOSHA_STATUS_KEY, "RUNNING", 24, TimeUnit.HOURS);
            redisUtil.set(MIAOSHA_RESUME_TIME_KEY, String.valueOf(currentTime), 24, TimeUnit.HOURS);
            
            // 删除暂停相关信息
            redisUtil.delete(MIAOSHA_PAUSE_TIME_KEY);
            redisUtil.delete(MIAOSHA_OPERATOR_KEY);
            redisUtil.delete(MIAOSHA_REASON_KEY);
            
            LOGGER.info("秒杀活动已恢复，时间: {}", currentTime);
            return true;
        } catch (Exception e) {
            LOGGER.error("恢复秒杀活动失败: {}", e.getMessage(), e);
            return false;
        }
    }
    
    @Override
    public boolean isMiaoshaPaused() {
        try {
            Object statusObj = redisUtil.get(MIAOSHA_STATUS_KEY);
            String status = statusObj != null ? statusObj.toString() : "RUNNING";
            return "PAUSED".equals(status);
        } catch (Exception e) {
            LOGGER.error("检查秒杀活动状态失败: {}", e.getMessage(), e);
            return false; // 默认允许秒杀
        }
    }
    
    @Override
    public String getMiaoshaStatus() {
        try {
            Object statusObj = redisUtil.get(MIAOSHA_STATUS_KEY);
            String status = statusObj != null ? statusObj.toString() : "RUNNING";
            return status;
        } catch (Exception e) {
            LOGGER.error("获取秒杀活动状态失败: {}", e.getMessage(), e);
            return "RUNNING"; // 默认状态
        }
    }
    
    @Override
    public MiaoshaStatus getMiaoshaStatusDetail() {
        try {
            Object statusObj = redisUtil.get(MIAOSHA_STATUS_KEY);
            String status = statusObj != null ? statusObj.toString() : "RUNNING";
            
            MiaoshaStatus miaoshaStatus = new MiaoshaStatus();
            miaoshaStatus.setPaused("PAUSED".equals(status));
            miaoshaStatus.setStatus(status);
            
            if ("PAUSED".equals(status)) {
                Object pauseTimeObj = redisUtil.get(MIAOSHA_PAUSE_TIME_KEY);
                if (pauseTimeObj != null) {
                    miaoshaStatus.setPauseTime(Long.parseLong(pauseTimeObj.toString()));
                }
                
                Object operatorObj = redisUtil.get(MIAOSHA_OPERATOR_KEY);
                miaoshaStatus.setOperator(operatorObj != null ? operatorObj.toString() : "admin");
                
                Object reasonObj = redisUtil.get(MIAOSHA_REASON_KEY);
                miaoshaStatus.setReason(reasonObj != null ? reasonObj.toString() : "库存维护");
            } else {
                Object resumeTimeObj = redisUtil.get(MIAOSHA_RESUME_TIME_KEY);
                if (resumeTimeObj != null) {
                    miaoshaStatus.setResumeTime(Long.parseLong(resumeTimeObj.toString()));
                }
            }
            
            return miaoshaStatus;
        } catch (Exception e) {
            LOGGER.error("获取秒杀活动状态详情失败: {}", e.getMessage(), e);
            return new MiaoshaStatus(false, "RUNNING");
        }
    }
}
