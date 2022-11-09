package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.URLUtil;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * @Description Redis锁实现
 * @Version 1.0.0
 * @Date 2022/11/9
 * @Author wandaren
 */
public class SimpleRedisLock implements ILock {
    private final String name;
    private final StringRedisTemplate stringRedisTemplate;
    private static final String KEY_PREFIX = "lock:";
    private static final String ID_PREFIX = UUID.randomUUID(true) + "-";
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;

    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    public SimpleRedisLock(String name, StringRedisTemplate redisTemplate) {
        this.name = name;
        this.stringRedisTemplate = redisTemplate;
    }

    @Override
    public boolean tryLock(long timeoutSec, TimeUnit unit) {
        // 获取线程标识
        final String threadId = ID_PREFIX + Thread.currentThread().getId();
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + name, threadId, timeoutSec, unit);
        return Boolean.TRUE.equals(flag);
//        return BooleanUtil.isTrue(flag);
    }

    //    @Override
//    public void unlock() {
//        // 获取线程标识
//        final String threadId = ID_PREFIX + Thread.currentThread().getId();
//        // 获取存放到id标识
//        final String id = redisTemplate.opsForValue().get(KEY_PREFIX + name);
//        // 相同才释放
//        if (threadId.equals(id)) {
//            redisTemplate.delete(KEY_PREFIX + name);
//        }
//    }
    @Override
    public void unlock() {
        // 获取线程标识
        final String threadId = ID_PREFIX + Thread.currentThread().getId();
        // 调用lua脚本
        stringRedisTemplate.execute(UNLOCK_SCRIPT,
                Collections.singletonList(KEY_PREFIX + name),
                threadId);
    }
}
