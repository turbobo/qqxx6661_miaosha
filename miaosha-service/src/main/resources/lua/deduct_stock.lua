-- deduct_stock.lua
-- Redis Lua 原子库存扣减脚本
-- KEYS[1]: ticket:stock:{date}
-- ARGV[1]: 扣减数量（通常为1）
local key = KEYS[1]
local count = tonumber(ARGV[1])
local current = tonumber(redis.call('GET', key) or '0')
if current >= count then
    redis.call('DECRBY', key, count)
    return 1  -- 扣减成功
else
    return 0  -- 库存不足
end
