package cn.monitor4all.miaoshaservice.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redisson 配置类。
 * <p>
 * 基于 Spring Boot 的 Redis 配置属性自动装配 RedissonClient，
 * 使用单节点模式，适用于当前项目的单机 Redis 部署架构。
 * </p>
 *
 * @author system
 */
@Configuration
public class RedissonConfig {

    @Value("${spring.redis.host:localhost}")
    private String host;

    @Value("${spring.redis.port:6379}")
    private String port;

    @Value("${spring.redis.password:}")
    private String password;

    @Value("${spring.redis.database:0}")
    private int database;

    /**
     * 创建 RedissonClient 实例。
     * <p>
     * 连接池参数说明：
     * <ul>
     *     <li>connectionPoolSize=64：最大连接数</li>
     *     <li>connectionMinimumIdleSize=16：最小空闲连接数</li>
     *     <li>idleConnectionTimeout=10000ms：空闲连接超时</li>
     *     <li>connectTimeout=10000ms：连接超时</li>
     *     <li>timeout=3000ms：命令响应超时</li>
     *     <li>retryAttempts=3：重试次数</li>
     *     <li>retryInterval=1500ms：重试间隔</li>
     * </ul>
     *
     * @return RedissonClient 实例
     */
    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        Config config = new Config();
        String address = "redis://" + host + ":" + port;
        SingleServerConfig serverConfig = config.useSingleServer()
                .setAddress(address)
                .setDatabase(database)
                .setConnectionPoolSize(64)
                .setConnectionMinimumIdleSize(16)
                .setIdleConnectionTimeout(10000)
                .setConnectTimeout(10000)
                .setTimeout(3000)
                .setRetryAttempts(3)
                .setRetryInterval(1500);

        if (password != null && !password.isEmpty()) {
            serverConfig.setPassword(password);
        }

        return Redisson.create(config);
    }
}
