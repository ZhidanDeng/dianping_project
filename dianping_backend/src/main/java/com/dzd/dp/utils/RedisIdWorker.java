package com.dzd.dp.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Created with IntelliJ IDEA.
 *
 * @Author: DZD
 * @Date: 2023/03/30/16:33
 * @Description:
 */
@Component
public class RedisIdWorker {

    private static final long BEGIN_TIMESTAMP = 1640995200L;
    private static final int COUNT_BITS = 32;
    @Autowired
    private StringRedisTemplate template;

    public long nextId(String kerPrefix){
        //最高位(表示正数即可)+时间戳（31位）+序列号（32位)
        LocalDateTime now = LocalDateTime.now();
        long nowSeconds = now.toEpochSecond(ZoneOffset.UTC);
        long timeStamp = nowSeconds - BEGIN_TIMESTAMP;

        String today = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        //自增长
        long count = template.opsForValue().increment("incr:" + kerPrefix + ":" + today);

        //位运算拼接
        return timeStamp << COUNT_BITS | count;
    }
}
