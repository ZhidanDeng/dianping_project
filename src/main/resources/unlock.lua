if(redis.call('get',KEYS[1]) == ARGV[1]) then
	-- 一致释放锁
	return redis.call('del',KEYS[1])
end
-- 不一致直接返回
return 0