package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import com.hmdp.utils.SystemConstants;
import lombok.extern.log4j.Log4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author wandaren
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    private static final ExecutorService CACHERE_BUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryById(Long id) {
        // 缓存穿透
//        Shop shop = queryWithPassThrough(id);
        // 互斥锁解决缓存击穿
//        Shop shop = queryWithMutex(id);
        // 逻辑过期解决缓存击穿
        Shop shop = queryWithLogicalExpire(id);
        if (shop == null) {
            Result.fail("店铺不存在");
        }
        return Result.ok(shop);
    }

    /**
     * 缓存击穿
     *
     * @param id
     * @return
     */
    public Shop queryWithMutex(Long id) {
        // 从redis查询商铺缓存
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        final String shopJson = stringRedisTemplate.opsForValue().get(key);
        // 判断是否存在缓存
        if (!ObjectUtils.isEmpty(shopJson)) {
            // 存在直接返回
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        if (shopJson != null) {
            return null;
        }
        // 实现缓存重建，获取互斥锁
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        // 判断是否获取锁成功
        final Shop shop;
        try {
            if (!tryLock(lockKey)) {
                // 失败，休眠并重试
                TimeUnit.MICROSECONDS.sleep(50);
                return queryWithMutex(id);
            }

            // 成功，查询数据库
            shop = getById(id);
            // 模拟重建缓存的延迟
            TimeUnit.MICROSECONDS.sleep(200);
            if (shop == null) {
                // 数据库不存在提示，并缓存10秒空值
                stringRedisTemplate.opsForValue().set(key, "", RedisConstants.LOCK_SHOP_TTL, TimeUnit.SECONDS);
                return null;
            }
            // 数据库存在，写入redis
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            // 释放互斥锁
            unLock(lockKey);
        }

        return shop;
    }

    /**
     * 缓存穿透
     *
     * @param id
     * @return
     */
    public Shop queryWithPassThrough(Long id) {
        // 从redis查询商铺缓存
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        final String shopJson = stringRedisTemplate.opsForValue().get(key);
        // 判断是否存在缓存
        if (!ObjectUtils.isEmpty(shopJson)) {
            // 存在直接返回
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        if (shopJson != null) {
            return null;
        }
        // 不存在查询数据库
        final Shop shop = getById(id);
        if (shop == null) {
            // 数据库不存在提示，并缓存10秒空值
            stringRedisTemplate.opsForValue().set(key, "", RedisConstants.LOCK_SHOP_TTL, TimeUnit.SECONDS);
            return null;
        }
        // 数据库存在，写入redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);

        return shop;
    }


    /**
     * 逻辑过期
     *
     * @param id
     * @return
     */
    public Shop queryWithLogicalExpire(Long id) {
        // 从redis查询商铺缓存
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        final String shopJson = stringRedisTemplate.opsForValue().get(key);
        // 判断是否存在缓存
        if (ObjectUtils.isEmpty(shopJson)) {
            // 不存在直接返回
            return null;
        }
        // 存在判断是否逻辑过期
        final RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        final LocalDateTime expireTime = redisData.getExpireTime();
        final Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
        // 未过期直接返回
        if (expireTime.isAfter(LocalDateTime.now())) {
            return shop;
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
                    this.saveShop2Redis(id, 20L);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    // 释放锁
                    unLock(lockKey);
                }

            });
        }
        // 获取锁失败，直接返回

        return shop;
    }

    @Override
    @Transactional
    public Result updateShop(Shop shop) {
        final Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺ID不能为空！");
        }
        String key = RedisConstants.CACHE_SHOP_KEY + id;

        // 1、更新数据库
        updateById(shop);

        // 2、删除缓存
        stringRedisTemplate.delete(key);
        return Result.ok();
    }

    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unLock(String key) {
        stringRedisTemplate.delete(key);
    }

    /**
     * 封装店铺逻辑过期时间数据
     *
     * @param id
     * @param expireSeconds
     */
    public void saveShop2Redis(Long id, Long expireSeconds) throws InterruptedException {
        // 查询店铺数据
        final Shop shop = getById(id);
        // 模拟消耗时间
        TimeUnit.MILLISECONDS.sleep(200L);
        // 封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        // 测试设置为秒
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        // 写入redis
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }

}
