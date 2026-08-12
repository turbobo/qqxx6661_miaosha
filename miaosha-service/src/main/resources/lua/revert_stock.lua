-- revert_stock.lua
-- Redis Lua 库存回补脚本（订单创建失败时调用）
-- KEYS[1]: ticket:stock:{date}
-- ARGV[1]: 回补数量（通常为1）
local key = KEYS[1]
local count = tonumber(ARGV[1])
redis.call('INCRBY', key, count)
return 1
