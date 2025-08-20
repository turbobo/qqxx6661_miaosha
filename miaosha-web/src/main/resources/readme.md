

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