-- 限流逻辑：限制用户在指定时间窗口内的最大请求次数
-- 参数说明：
-- KEYS[1]：限流键（格式：user:limit:{userId}）
-- ARGV[1]：最大请求次数（如10次）
-- ARGV[2]：时间窗口（单位：秒，如60秒）

-- 1. 获取当前用户的请求计数
local current = tonumber(redis.call('get', KEYS[1]) or "0")

-- 2. 判断是否超过限制
if current >= tonumber(ARGV[1]) then
    -- 超过限制，返回0（拒绝请求）
    return 0
end

-- 3. 未超过限制，计数+1，并设置过期时间
redis.call('incr', KEYS[1])
-- 首次设置时需要设置过期时间（避免永久留存）
if current == 0 then
    redis.call('expire', KEYS[1], ARGV[2])
end

-- 4. 返回1（允许请求）
return 1