package com.hmdp;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONUtil;
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
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

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
    @Resource
    RedissonClient redissonClient2;

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
    void name3() {
        String queueName = "stream.orders";
        List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                // GROUP g1 c1
                Consumer.from("g1", "c1"),
                // COUNT 1 BLOCK 2000
                StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                // STREAMS s1 >
                StreamOffset.create(queueName, ReadOffset.lastConsumed())
        );
        final MapRecord<String, Object, Object> record = list.get(0);
        Map<Object, Object> value = record.getValue();
        final VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
        System.out.println(JSONUtil.toJsonStr(voucherOrder));

    }
}
