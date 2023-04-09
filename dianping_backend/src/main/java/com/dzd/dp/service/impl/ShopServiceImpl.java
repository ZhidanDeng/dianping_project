package com.dzd.dp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dzd.dp.dto.Result;
import com.dzd.dp.entity.Shop;
import com.dzd.dp.mapper.ShopMapper;
import com.dzd.dp.service.IShopService;
import com.dzd.dp.utils.CacheClient;
import com.dzd.dp.utils.RedisData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.dzd.dp.constant.Constant.*;
import static com.dzd.dp.utils.SystemConstants.DEFAULT_PAGE_SIZE;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Autowired
    private StringRedisTemplate template;

    @Autowired
    private CacheClient cacheClient;

    @Override
    public Result queryById(Long id) {

        Shop shop = cacheClient.
                queryWithPassThrough(CACHE_SHOP, id, Shop.class,
                        this::getById, CACHE_SHOP_EXPIRED, TimeUnit.MINUTES);

        if (shop == null) {
            return Result.fail("店铺不存在");
        }
        return Result.ok(shop);
    }

    //解决击穿,互斥锁方法
//    public Shop queryWithMutex(Long id){
//        //防止缓存击穿
//        //使用互斥锁
//        String key = CACHE_SHOP + id;
//        String shopJson = template.opsForValue().get(key);
//        if (!StrUtil.isBlank(shopJson)) {//only null --> true,else returns false
//            return JSONUtil.toBean(shopJson, Shop.class);
//        }
//        //是否为空对象
//        if (shopJson != null) {
//            return null;
//        }
//        Shop shop = null;
//        try {
//            //防止击穿,缓存重建
//            //获取互斥锁
//            String lockKey = LOCK_SHOP + id;
//            boolean lock = tryLock(lockKey);
//            if (!lock) {
//                //失败则休眠并重试
//                Thread.sleep(50);
//                return queryWithMutex(id);
//            }
//            //拿到锁后double check
//            shop = JSONUtil.toBean(template.opsForValue().get(key),Shop.class);
//            if (shop!=null){
//                return shop;
//            }
//            //判断是否成功
//            shop = getById(id);
//            if (shop == null) {
//                template.opsForValue().set(key, "", CACHE_NULL_EXPIRED, TimeUnit.MINUTES);
//                return null;
//            }
//            template.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_EXPIRED, TimeUnit.MINUTES);
//        } catch (InterruptedException e) {
//            throw new RuntimeException(e);
//        } finally {
//            unlock(key);
//        }
//        return shop;
//    }

    //防止缓存穿透
//    public Shop queryWithPassThrough(Long id) {
//        //从redis查询商铺缓存
//        String key = CACHE_SHOP + id;
//        String shopJson = template.opsForValue().get(key);
//        //判断是否存在,存在则直接返回
//        if (!StrUtil.isBlank(shopJson)) {
//            return JSONUtil.toBean(shopJson, Shop.class);
//        }
//        //命中空对象
//        if (shopJson != null) {
//            return null;
//        }
//        //不存在则查询数据库，数据库要是没有该商铺则返回404，存在则先写入redis,再返回结果
//        Shop shop = getById(id);
//        if (shop == null) {
//            //不存在，则写入空对象，防止穿透问题
//            template.opsForValue().set(key, "", CACHE_NULL_EXPIRED, TimeUnit.MINUTES);
//            return null;
//        } else {
//            String shopJsonStr = JSONUtil.toJsonStr(shop);
//            template.opsForValue().set(CACHE_SHOP + id, shopJsonStr, CACHE_SHOP_EXPIRED, TimeUnit.MINUTES);
//            return shop;
//        }
//    }
    public void saveShop2Redis(Long id, Long expiredSeconds) {
        Shop shop = getById(id);
        String key = CACHE_SHOP + id;
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        //设置逻辑过期时间
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expiredSeconds));
        template.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    @Override
    @Transactional
    public Result updateShop(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺id不能为空");
        }
        //更新数据库，再删缓存，同时保证两个操作的原子性
        updateById(shop);
        String key = CACHE_SHOP + id;
        template.delete(key);
        return Result.ok();
    }

    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        //判断是否需要根据坐标擦查询
        if (x == null || y == null) {
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, DEFAULT_PAGE_SIZE));
            return Result.ok(page.getRecords());
        }
        //计算分页参数
        int from = (current - 1) * DEFAULT_PAGE_SIZE;
        int end = current * DEFAULT_PAGE_SIZE;
        String key = SHOP_GEO_KEY+typeId;
        //查询redis,按距离排序，分页。结果：shopId,distance
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = template.opsForGeo()
                .search(
                        key,
                        GeoReference.fromCoordinate(x, y),
                        new Distance(5000),
                        RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end)
                );
        if (results==null){
            return Result.ok(Collections.emptyList());
        }

        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();
        if (from>= list.size()){
            return Result.ok(Collections.emptyList());
        }
        List<Long> ids = new ArrayList<>(list.size());
        Map<String,Distance> distanceMap = new HashMap<>(list.size());
        //截取[from,end]
        list.stream().skip(from).forEach(r->{
            String shopIdStr = r.getContent().getName();
            ids.add(Long.valueOf(shopIdStr));
            Distance distance = r.getDistance();
            distanceMap.put(shopIdStr,distance);
        });
        //根据id查询shop
        String idStr = StrUtil.join(",", ids);
        List<Shop> shops = query().in("id", ids).last("order by field(id," + idStr + ")").list();
        for (Shop shop : shops) {
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
        }
        return Result.ok(shops);
    }
}
