

用 Redis+Lua 脚本实现原子性操作，适合多实例部署的系统（如秒杀接口）。
Lua 脚本（限制用户每秒最多 3 次请求）：

-- 限流key：user:limit:{用户ID}
local key = KEYS[1]
-- 限流次数
local limit = tonumber(ARGV[1])
-- 限流时间窗口（秒）
local window = tonumber(ARGV[2])

-- 当前请求次数
local current = tonumber(redis.call('get', key) or "0")
if current >= limit then
    return 0  -- 超出限制
end

-- 次数+1，设置过期时间
redis.call('incr', key)
redis.call('expire', key, window)
return 1  -- 允许请求

## 页面入口

- `purchase-version-comparison.html`：抢票版本方案效果对比页，对比 V1 悲观锁、V1 乐观锁、V2 异步 MQ、V3 实验链路的核心流程、效果指标、瓶颈与适用阶段，并提供单版本接口实测面板。
