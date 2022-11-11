package com.hmdp;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisWorker;
import lombok.extern.log4j.Log4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoLocation;

import javax.annotation.Resource;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.stream.Collector;
import java.util.stream.Collectors;

@SpringBootTest
class HmDianPingApplicationTests {
    @Autowired
    ShopServiceImpl service;
    @Autowired
    StringRedisTemplate stringRedisTemplate;
    @Autowired
    RedisWorker redisWorker;
    @Resource
    RedissonClient redissonClient;


    private RLock lock;
    @BeforeEach
    void setUp(){
//        RLock lock1 =  redissonClient.getLock("order");
//        RLock lock2 =  redissonClient2.getLock("order");
//        lock = redissonClient.getMultiLock(lock1, lock2);
    }

    ExecutorService executorService = new ThreadPoolExecutor(400, 600,
            1L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>());

    @Test
    void name() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(300);
        final Runnable runnable = () -> {
            for (int i = 0; i < 100; i++) {
                final long qifeng = redisWorker.nextId("qifeng");
                System.out.println(qifeng);
            }
            latch.countDown();
        };
        final long l = System.currentTimeMillis();
        for (int i = 0; i < 300; i++) {
            executorService.submit(runnable);
        }
        latch.await();
        final long l1 = System.currentTimeMillis() - l;
        System.out.println(l1);
    }

    @Test
    void lock() throws InterruptedException {
        RLock lock1 =  redissonClient.getLock("order");
        final boolean tryLock = lock1.tryLock();
        if(!tryLock){
            System.out.println("获取锁失败！");
            return;
        }
        try {
            System.out.println("获取锁成功.......1");
        }finally {
            lock1.unlock();
        }
    }

    @Test
    void name2() {
        final boolean tryLock = lock.tryLock();
        if(!tryLock){
            System.out.println("获取锁失败！.......2");
            return;
        }
        try {
            System.out.println("获取锁成功.......2");
        }finally {
            lock.unlock();
        }
    }

    @Test
    void loadShopData() {
       // 查询店铺信息
        final List<Shop> list = service.list();
        // 把店铺分组，按照typeId分，ID一致的放到一个集合
        final Map<Long, List<Shop>> map = list.stream().collect(Collectors.groupingBy(Shop::getTypeId));
        // 分批完成写入redis
        for (Map.Entry<Long, List<Shop>> entry : map.entrySet()) {
            final Long typeId = entry.getKey();
            String key = RedisConstants.SHOP_GEO_KEY + typeId;
            // 获取同类型店铺
            final List<Shop> value = entry.getValue();
            // 写入redis
            List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>(value.size());
            for (Shop shop : value) {
                locations.add(new RedisGeoCommands.GeoLocation<>(shop.getId().toString(), new Point(shop.getX(), shop.getY())));
            }
            stringRedisTemplate.opsForGeo().add(key, locations);

        }
    }

    /**
     * UV统计-HyperLogLog
     */
    @Test
    void testHyperLogLog() {
        String[] values = new String[1000];
        int j;
        for (int i = 0; i < 1000000; i++) {
            j = i % 1000;
            values[j] = "user_" + i;
            if (j == 999) {
                stringRedisTemplate.opsForHyperLogLog().add("hl2", values);
            }
        }
        // 统计
        final Long hl2 = stringRedisTemplate.opsForHyperLogLog().size("hl2");
        System.out.println(hl2);
    }
}
