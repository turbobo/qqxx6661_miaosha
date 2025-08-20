-- 分布式限流Lua脚本
-- 实现滑动时间窗口限流算法
-- 参数说明：
-- KEYS[1]: 限流键
-- ARGV[1]: 限流次数
-- ARGV[2]: 时间窗口（秒）
-- ARGV[3]: 当前时间戳（秒）

-- 获取当前时间戳
local now = tonumber(ARGV[3])
-- 获取限流次数
local limit = tonumber(ARGV[1])
-- 获取时间窗口
local window = tonumber(ARGV[2])

-- 清理过期的记录（时间窗口外的记录）
local expired = now - window

-- 获取当前键的所有成员
local current = redis.call('ZRANGEBYSCORE', KEYS[1], 0, expired)
if #current > 0 then
    -- 删除过期的记录
    redis.call('ZREMRANGEBYSCORE', KEYS[1], 0, expired)
end

-- 获取当前时间窗口内的记录数量
local count = redis.call('ZCARD', KEYS[1])

-- 检查是否超过限流次数
if count >= limit then
    -- 超过限流次数，返回0表示限流
    return {0, count, now + window}
end

-- 未超过限流次数，添加当前请求记录
redis.call('ZADD', KEYS[1], now, now .. ':' .. math.random(1000000, 9999999))
-- 设置过期时间，避免内存泄漏
redis.call('EXPIRE', KEYS[1], window + 10)

-- 返回1表示成功，当前记录数，重置时间
return {1, count + 1, now + window}
