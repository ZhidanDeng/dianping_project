--lua
local lockId = KEYS[1]
local threadId = ARGV[1]
if(redis.call('get',lockId)==threadId) then
    --锁释放
    return redis.call('del',lockId)
end
return 0
