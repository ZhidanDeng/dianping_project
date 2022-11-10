package com.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.Resource;

/**
 * @Description TODO
 * @Version 1.0.0
 * @Date 2022/11/9
 * @Author wandaren
 */
@Configuration
public class RedissonConfig {

    @Bean
    public RedissonClient redissonClient(){
        // 配置
        Config config = new Config();
        config.useSingleServer().setAddress("redis://172.16.156.139:6379")
                .setPassword("qifeng");
        // 创建RedissonClient对象
        return Redisson.create(config);
    }

    @Bean
    public RedissonClient redissonClient2(){
        // 配置
//        Config config = new Config();
//        config.useSingleServer().setAddress("redis://114.67.111.175:6379")
//                .setPassword("qifeng");
        // 创建RedissonClient对象
//        return Redisson.create(config);

        return null;
    }
}
