if(redis.call('get',KEYS[1] == ARGV[1]) then
-- 执行释放锁 del key操作
return redis.call('del',KEYS[1])
end
return 0

