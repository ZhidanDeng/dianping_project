package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.URLUtil;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

/**
 * @Description Redis锁实现
 * @Version 1.0.0
 * @Date 2022/11/9
 * @Author wandaren
 */
public class SimpleRedisLock implements ILock {
    private String name;
    private StringRedisTemplate redisTemplate;
    private static final String KEY_PREFIX = "lock:";
    private static final String ID_PREFIX = UUID.randomUUID(true) + "-";

    public SimpleRedisLock(String name, StringRedisTemplate redisTemplate) {
        this.name = name;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean tryLock(long timeoutSec, TimeUnit unit) {
        // 获取线程标识
        final String threadId = ID_PREFIX + Thread.currentThread().getId();
        Boolean flag = redisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + name, threadId, timeoutSec, unit);
        return Boolean.TRUE.equals(flag);
//        return BooleanUtil.isTrue(flag);
    }

    @Override
    public void unLock() {
        // 获取线程标识
        final String threadId = ID_PREFIX + Thread.currentThread().getId();
        // 获取存放到id标识
        final String id = redisTemplate.opsForValue().get(KEY_PREFIX + name);
        // 相同才释放
        if (threadId.equals(id)) {
            redisTemplate.delete(KEY_PREFIX + name);
        }
    }
}
