-- rate_limit.lua 分布式令牌桶限流
-- KEYS[1]: 限流 key（全局接口路径或用户ID维度）
-- ARGV[1]: max_tokens  桶容量
-- ARGV[2]: refill_rate 每秒补充令牌数
-- ARGV[3]: now         当前时间戳（毫秒）
-- ARGV[4]: requested   本次请求消耗令牌数

local key = KEYS[1]
local max_tokens = tonumber(ARGV[1])
local refill_rate = tonumber(ARGV[2])
local now = tonumber(ARGV[3])
local requested = tonumber(ARGV[4])

local data = redis.call('HMGET', key, 'tokens', 'last_refill')
local tokens = tonumber(data[1]) or max_tokens
local last_refill = tonumber(data[2]) or now

-- 计算补充令牌（时间差转秒 * 补充速率）
local elapsed = (now - last_refill) / 1000
local new_tokens = math.min(max_tokens, tokens + elapsed * refill_rate)

if new_tokens >= requested then
    new_tokens = new_tokens - requested
    redis.call('HMSET', key, 'tokens', new_tokens, 'last_refill', now)
    redis.call('EXPIRE', key, 60)
    return 1
else
    redis.call('HMSET', key, 'tokens', new_tokens, 'last_refill', now)
    redis.call('EXPIRE', key, 60)
    return 0
end
