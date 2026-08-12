package cn.monitor4all.miaoshaservice.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 缓存失效事件监听器
 * <p>
 * 监听 Redis Pub/Sub channel {@code cache:invalidate}，
 * 收到其他节点广播的失效消息时，失效本地 L1 缓存。
 */
@Component
public class CacheInvalidateListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(CacheInvalidateListener.class);

    private static final String CHANNEL = MultiLevelCacheManager.CACHE_INVALIDATE_CHANNEL;

    @Autowired
    private MultiLevelCacheManager cacheManager;

    /**
     * 注册 Redis Pub/Sub 监听容器
     */
    @Bean
    public RedisMessageListenerContainer cacheInvalidateListenerContainer(
            RedisConnectionFactory connectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(cacheInvalidateMessageListener(), new ChannelTopic(CHANNEL));
        LOGGER.info("缓存失效监听器已注册, channel={}", CHANNEL);
        return container;
    }

    /**
     * 创建消息监听器
     */
    private MessageListener cacheInvalidateMessageListener() {
        return (message, pattern) -> {
            try {
                String key = new String(message.getBody(), StandardCharsets.UTF_8);
                LOGGER.debug("收到缓存失效广播, key={}", key);
                cacheManager.evictLocal(key);
            } catch (Exception e) {
                LOGGER.error("处理缓存失效广播消息异常", e);
            }
        };
    }
}
