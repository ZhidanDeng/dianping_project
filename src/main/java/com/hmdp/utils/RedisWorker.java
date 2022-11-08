package com.hmdp.utils;

import cn.hutool.core.date.DatePattern;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.date.LocalDateTimeUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;

/**
 * @Description 生成全局唯一id
 * @Version 1.0.0
 * @Date 2022/11/8
 * @Author wandaren
 */
@Component
public class RedisWorker {
    /**
     * 开始时间戳
     */
    private static final long BEGIN_TIMESTAMP = 1667865600L;
    /**
     * 序列号到位数
     */
    private static final int COUNT_BITS = 32;
    private final StringRedisTemplate stringRedisTemplate;

    public RedisWorker(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }


    public long nextId(String keyPrefix) {
        // 1 生成时间戳
        LocalDateTime now = LocalDateTime.now();
        final long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        final long timestamp = nowSecond - BEGIN_TIMESTAMP;
        // 2、生成序列号
        final String date = DateUtil.format(new Date(), DatePattern.PURE_DATE_PATTERN);
        final long increment = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);
        // 左移32位
        return timestamp << COUNT_BITS | increment;
    }

}
