package com.hmdp;

import cn.hutool.json.JSONUtil;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisWorker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.ObjectUtils;

import java.util.concurrent.*;

@SpringBootTest
class HmDianPingApplicationTests {
    @Autowired
    ShopServiceImpl service;
    @Autowired
    StringRedisTemplate stringRedisTemplate;
    @Autowired
    RedisWorker redisWorker;

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
}
