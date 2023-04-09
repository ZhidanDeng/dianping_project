package com.dzd.dp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * Created with IntelliJ IDEA.
 *
 * @Author: DZD
 * @Date: 2023/03/31/21:34
 * @Description:
 */
public class SimpleRedisLock implements ILock{

    private static final String KEY_PREFIX = "lock:";
    private static final String ID_PREFIX = UUID.randomUUID().toString(true)+"-";
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    private String name;
    private StringRedisTemplate template;

    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    public SimpleRedisLock(String name, StringRedisTemplate template) {
        this.name = name;
        this.template = template;
    }

    @Override
    public boolean tryLock(long timeoutSec) {
        String threadId =ID_PREFIX+ Thread.currentThread().getId();
        Boolean success = template.opsForValue()
                .setIfAbsent(KEY_PREFIX + name, threadId, timeoutSec, TimeUnit.SECONDS);
        //避免空指针
        return Boolean.TRUE.equals(success);
    }

    @Override
    public void unLock() {
        //调用lua脚本进行锁释放，保证原子性
        //提前读取脚本
        template.execute(
                UNLOCK_SCRIPT,
                Collections.singletonList(KEY_PREFIX + name),
                ID_PREFIX+ Thread.currentThread().getId()
        );
    }

    //
//    @Override
//    public void unLock() {
//        //获取锁标识
//        String flag = template.opsForValue().get(KEY_PREFIX + name);
//        String threadId =ID_PREFIX+ Thread.currentThread().getId();
//        if (flag.equals(threadId)){
//            //一致再删除
//            template.delete(KEY_PREFIX + name);
//        }
//    }
}
