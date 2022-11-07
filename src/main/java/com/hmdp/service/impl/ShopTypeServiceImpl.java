package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
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
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public Result queryTypeList() {
        final List<String> shopTypeList = stringRedisTemplate.opsForList().range(RedisConstants.TYPE_SHOP_KEY, 0, -1);
        if (!ObjectUtils.isEmpty(shopTypeList)) {
            if (shopTypeList.get(0).equals("1")) {
                return Result.ok("程序错误，未找到商铺类型数据！！！");
            }
            List<ShopType> typeList = new ArrayList<>();
            shopTypeList.forEach(type ->
                    typeList.add(JSONUtil.toBean(type, ShopType.class))
            );
            return Result.ok(typeList);
        }
        List<ShopType> typeList = query().orderByAsc("sort").list();
        if (ObjectUtils.isEmpty(typeList)) {
            stringRedisTemplate.opsForList().leftPush(RedisConstants.TYPE_SHOP_KEY, "1");
            stringRedisTemplate.expire(RedisConstants.TYPE_SHOP_KEY, RedisConstants.LOCK_SHOP_TTL, TimeUnit.SECONDS);
            return Result.ok("程序错误，未找到商铺类型数据！！！");
        }

        List<String> list = new ArrayList<>();
        typeList.forEach(type ->
                list.add(JSONUtil.toJsonStr(type))
        );
        stringRedisTemplate.opsForList().leftPushAll(RedisConstants.TYPE_SHOP_KEY, list);

        return Result.ok(typeList);
    }
}
