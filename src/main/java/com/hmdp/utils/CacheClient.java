package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.TimeoutUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * @Description 缓存工具类
 * @Version 1.0.0
 * @Date 2022/11/8
 * @Author wandaren
 */
@Component
public class CacheClient {

    private final StringRedisTemplate stringRedisTemplate;
    private static final ExecutorService CACHERE_BUILD_EXECUTOR = Executors.newFixedThreadPool(10);


    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }


    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unLock(String key) {
        stringRedisTemplate.delete(key);
    }

    /**
     * TTL过期
     *
     * @param key
     * @param value
     * @param time
     * @param unit
     */
    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    /**
     * 逻辑过期
     *
     * @param key
     * @param value
     * @param time
     * @param unit
     */
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData), time, unit);
    }

    /**
     * 存空值解决缓存穿透
     * @param keyPrefix
     * @param id
     * @param type
     * @param dbFallback
     * @param time
     * @param unit
     * @param <R>
     * @param <ID>
     * @return
     */
    public <R, ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type,
                                          Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        // 从redis查询商铺缓存
        String key = keyPrefix + id;
        final String shopJson = stringRedisTemplate.opsForValue().get(key);
        // 判断是否存在缓存
        if (!ObjectUtils.isEmpty(shopJson)) {
            // 存在直接返回
            return JSONUtil.toBean(shopJson, type);
        }
        if (shopJson != null) {
            return null;
        }
        // 不存在查询数据库
        final R r = dbFallback.apply(id);
        if (r == null) {
            // 数据库不存在提示，并缓存10秒空值
            stringRedisTemplate.opsForValue().set(key, "", RedisConstants.LOCK_SHOP_TTL, TimeUnit.SECONDS);
            return null;
        }
        // 数据库存在，写入redis
        this.set(key, JSONUtil.toJsonStr(r), time, unit);
        return r;
    }

    /**
     * 逻辑过期解决缓存击穿
     *
     * @param id
     * @return
     */
    public <R, ID> R queryWithLogicalExpire(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback,
                                            Long time, TimeUnit unit) {
        // 从redis查询商铺缓存
        String key = keyPrefix + id;
        final String shopJson = stringRedisTemplate.opsForValue().get(key);
        // 判断是否存在缓存
        if (ObjectUtils.isEmpty(shopJson)) {
            // 不存在直接返回
            return null;
        }
        // 存在判断是否逻辑过期
        final RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        final LocalDateTime expireTime = redisData.getExpireTime();
        final R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        // 未过期直接返回
        if (expireTime.isAfter(LocalDateTime.now())) {
            return r;
        }
        // 已过期，重建缓存
        // 获取互斥锁
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        // 判断获取锁是否成功
        if (tryLock(lockKey)) {
            // 获取锁成功，开启独立线程重建缓存
            CACHERE_BUILD_EXECUTOR.submit(() -> {
                try {
                    // 重建缓存
                    final R r1 = dbFallback.apply(id);
                    this.setWithLogicalExpire(key, r1, time, unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    // 释放锁
                    unLock(lockKey);
                }

            });
        }
        // 获取锁失败，直接返回
        return r;
    }
}
