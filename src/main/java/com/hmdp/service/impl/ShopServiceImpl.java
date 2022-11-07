package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import lombok.extern.log4j.Log4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;

import javax.annotation.Resource;
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

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryById(Long id) {
        // 从redis查询商铺缓存
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        final String shopJson = stringRedisTemplate.opsForValue().get(key);
        // 判断是否存在缓存
        if (!ObjectUtils.isEmpty(shopJson)) {
            // 存在直接返回
            final Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return Result.ok(shop);
        }
        if (shopJson != null) {
            return Result.fail("店铺不存在");
        }
        // 不存在查询数据库
        final Shop shop = getById(id);
        if (shop == null) {
            // 数据库不存在提示，并缓存10秒空值
            stringRedisTemplate.opsForValue().set(key, "", RedisConstants.LOCK_SHOP_TTL, TimeUnit.SECONDS);
            return Result.fail("店铺不存在");
        }
        // 数据库存在，写入redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);

        return Result.ok(shop);
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
}
