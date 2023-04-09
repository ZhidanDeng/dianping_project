package com.dzd.dp.utils;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.dzd.dp.constant.Constant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import cn.hutool.core.util.StrUtil;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Created with IntelliJ IDEA.
 *
 * @Author: DZD
 * @Date: 2023/03/28/20:42
 * @Description:
 */
@Slf4j
@Component
public class CacheClient {

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    private StringRedisTemplate template;

    public CacheClient(StringRedisTemplate template) {
        this.template = template;
    }

    public void set(String key, Object val, Long time, TimeUnit unit){
        String jsonStr = JSONUtil.toJsonStr(val);
        template.opsForValue().set(key,jsonStr,time,unit);
    }

    public void setWithLogicExpired(String key,Object val,Long time, TimeUnit unit){
        RedisData redisData = new RedisData();
        redisData.setData(val);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        template.opsForValue().set(key,JSONUtil.toJsonStr(redisData));
    }

    public <R,ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type, Function<ID,R> dbFallback, Long time, TimeUnit unit){
        String key = keyPrefix + id;
        String jsonStr = template.opsForValue().get(key);
        if (!StrUtil.isBlank(jsonStr)){
            return JSONUtil.toBean(jsonStr,type);
        }
        if (jsonStr != null){
            return null;
        }
        //Function<参数类型，返回值类型>
        R apply = dbFallback.apply(id);
        if (apply==null){
            template.opsForValue().set(key,"", Constant.CACHE_NULL_EXPIRED,TimeUnit.MINUTES);
            return null;
        }
        this.set(key,apply,time,unit);
        return apply;

    }

    //解决击穿,逻辑过期方法
    public <R,ID> R queryWithLogicExpired(String keyPrefix,ID id,Class<R> type,Function<ID,R> dbFallback,Long time,TimeUnit unit){
        String key = keyPrefix+id;
        String shopJson = template.opsForValue().get(key);
        if (StrUtil.isBlank(shopJson)){
            return null;
        }
        //判断是否过期
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        JSONObject data = (JSONObject) redisData.getData();
        R r = JSONUtil.toBean(data, type);
        LocalDateTime expireTime = redisData.getExpireTime();
        if (expireTime.isAfter(LocalDateTime.now())){
            return r;
        }
        //过期，需要重建缓存
        String lockKey = Constant.LOCK_SHOP + id;
        boolean lock = tryLock(lockKey);
        if (lock){
            //先double check
            r = JSONUtil.toBean(template.opsForValue().get(key), type);
            if (r!=null){
                return r;
            }
            //开启独立线程，让新线程重建
            CACHE_REBUILD_EXECUTOR.submit(()->{
                try {
                    R r1 = dbFallback.apply(id);
                    setWithLogicExpired(key,r1,time,unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    //释放锁
                    unlock(key);
                }
            });
        }
        return r;
    }
    private boolean tryLock(String key) {

        Boolean flag = template.opsForValue().setIfAbsent(key, "1", 10l, TimeUnit.SECONDS);
        //不能直接返回flag，拆箱过程中可能为空
        return cn.hutool.core.util.BooleanUtil.isTrue(flag);
    }


    private void unlock(String key) {
        template.delete(key);
    }

}
