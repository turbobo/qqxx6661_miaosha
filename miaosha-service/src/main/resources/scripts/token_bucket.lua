-- 令牌桶限流Lua脚本
-- 实现令牌桶算法
-- 参数说明：
-- KEYS[1]: 限流键
-- ARGV[1]: 桶容量
-- ARGV[2]: 令牌填充速率（每秒）
-- ARGV[3]: 当前时间戳（秒）
-- ARGV[4]: 请求令牌数（默认1）

-- 获取参数
local capacity = tonumber(ARGV[1])
local rate = tonumber(ARGV[2])
local now = tonumber(ARGV[3])
local tokens = tonumber(ARGV[4]) or 1

-- 获取当前令牌桶状态
local bucket = redis.call('HMGET', KEYS[1], 'tokens', 'lastRefillTime')
local currentTokens = tonumber(bucket[1]) or capacity
local lastRefillTime = tonumber(bucket[2]) or now

-- 计算需要填充的令牌数
local timePassed = now - lastRefillTime
local tokensToAdd = math.floor(timePassed * rate)

-- 更新令牌数量（不超过桶容量）
currentTokens = math.min(capacity, currentTokens + tokensToAdd)

-- 检查是否有足够的令牌
if currentTokens < tokens then
    -- 令牌不足，返回0表示限流
    return {0, currentTokens, now + math.ceil((tokens - currentTokens) / rate)}
end

-- 消耗令牌
currentTokens = currentTokens - tokens

-- 更新令牌桶状态
redis.call('HMSET', KEYS[1], 'tokens', currentTokens, 'lastRefillTime', now)
-- 设置过期时间，避免内存泄漏
redis.call('EXPIRE', KEYS[1], math.ceil(capacity / rate) + 10)

-- 返回1表示成功，剩余令牌数，下次填充时间
return {1, currentTokens, now + math.ceil((capacity - currentTokens) / rate)}
