package cn.monitor4all.miaoshaweb.controller;

import cn.monitor4all.miaoshadao.model.ApiResponse;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

/**
 * MQ监控接口
 */
@RestController
@RequestMapping("/api/mq")
@CrossOrigin(origins = "*")
public class MqMonitorController {
    
    @Resource
    private RabbitAdmin rabbitAdmin;
    
    /**
     * 获取MQ队列状态
     */
    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> getMqStatus() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // 获取队列信息
            org.springframework.amqp.core.QueueInformation queueInfo = rabbitAdmin.getQueueInfo("miaosha.purchase.queue");
            
            if (queueInfo != null) {
                result.put("queueName", "miaosha.purchase.queue");
                result.put("messageCount", queueInfo.getMessageCount());
                result.put("consumerCount", queueInfo.getConsumerCount());
                result.put("status", "正常");
                result.put("timestamp", System.currentTimeMillis());
            } else {
                result.put("status", "队列不存在");
            }
            
        } catch (Exception e) {
            result.put("status", "错误: " + e.getMessage());
        }
        
        return ApiResponse.success(result);
    }
}
