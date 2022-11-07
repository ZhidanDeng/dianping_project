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
        // 不存在查询数据库
        final Shop shop = getById(id);
        if (shop == null) {
            // 数据库不存在提示，并缓存10秒null值
            stringRedisTemplate.opsForValue().set(key, null, RedisConstants.LOCK_SHOP_TTL, TimeUnit.SECONDS);
            return Result.fail("店铺不存在");
        }
        // 数据库存在，写入redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop));

        return Result.ok(shop);
    }
}
