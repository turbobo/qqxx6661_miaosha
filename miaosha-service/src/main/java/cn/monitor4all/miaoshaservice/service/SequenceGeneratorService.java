package cn.monitor4all.miaoshaservice.service;

/**
 * 序列号生成服务接口
 * 提供多种策略生成连续、唯一的序列号
 */
public interface SequenceGeneratorService {
    
    /**
     * 生成下一个序列号
     * @param businessKey 业务键（如日期）
     * @return 下一个序列号
     */
    long getNextSequence(String businessKey);
    
    /**
     * 生成下一个序列号（带步长）
     * @param businessKey 业务键
     * @param step 步长
     * @return 下一个序列号
     */
    long getNextSequence(String businessKey, long step);
    
    /**
     * 获取当前序列号
     * @param businessKey 业务键
     * @return 当前序列号
     */
    long getCurrentSequence(String businessKey);
    
    /**
     * 重置序列号
     * @param businessKey 业务键
     * @param startValue 起始值
     */
    void resetSequence(String businessKey, long startValue);
    
    /**
     * 获取序列号生成策略信息
     * @return 策略信息
     */
    String getGenerationStrategy();
}
