package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
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

    public SimpleRedisLock(String name, StringRedisTemplate redisTemplate) {
        this.name = name;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean tryLock(long timeoutSec, TimeUnit unit) {
        final long id = Thread.currentThread().getId();
        Boolean flag = redisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + name, String.valueOf(id), timeoutSec, unit);
        return Boolean.TRUE.equals(flag);
//        return BooleanUtil.isTrue(flag);
    }

    @Override
    public void unLock() {
        redisTemplate.delete(KEY_PREFIX + name);
    }
}
