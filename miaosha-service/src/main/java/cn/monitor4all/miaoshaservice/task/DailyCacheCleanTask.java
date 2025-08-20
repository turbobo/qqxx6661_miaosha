package cn.monitor4all.miaoshaservice.task;

import cn.monitor4all.miaoshadao.utils.CacheKey;
import lombok.extern.slf4j.Slf4j;
import javax.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

@Slf4j
@EnableScheduling
@Component
public class DailyCacheCleanTask {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 每天21点执行缓存清理任务
     */
    @Scheduled(cron = "0 0 21 * * ?")
    public void cleanUserHasOrderCache() {
        try {
            // 匹配所有USER_HAS_ORDER相关的key
            String pattern = CacheKey.USER_HAS_ORDER.getKey() + "*";
            Set<String> keys = stringRedisTemplate.keys(pattern);
            
            if (keys != null && !keys.isEmpty()) {
                // 删除匹配的keys
                stringRedisTemplate.delete(keys);
                log.info("成功清理USER_HAS_ORDER缓存，共删除{}个key", keys.size());
            } else {
                log.info("未找到USER_HAS_ORDER相关缓存");
            }
        } catch (Exception e) {
            log.error("清理USER_HAS_ORDER缓存失败", e);
        }
    }
}