package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.log4j.Log4j2;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * redis判断，购买资格与库存，使用redis的stream
 *
 * 先连接redis客户端手动执行： XGROUP CREATE stream.orders g1 $ MKSTREAM
 * $代表队列中最后一个消息，0则代表队列中第一个消息（队列中已有消息并且想要消费掉使用0，不想消费或者队列中没有消息使用$）
 * @author wandaren
 * @since 2021-12-22
 */
@Log4j2
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Autowired
    private ISeckillVoucherService iSeckillVoucherService;
    @Autowired
    private RedisWorker redisWorker;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    RedissonClient redissonClient;
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;

    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    private ExecutorService SEKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();
    private IVoucherOrderService proxy;

    private class VoucherOrderHandler implements Runnable {
        String queueName = "stream.orders";
        @Override
        public void run() {
            while (true) {
                try {
                    // 1、获取消息队列中的订单信息 XREADGROUP GROUP g1 c1 COUNT 1 BLOCK 2000 STREAMS s1 >
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            // GROUP g1 c1
                            Consumer.from("g1", "c1"),
                            // COUNT 1 BLOCK 2000
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            // STREAMS s1 >
                            StreamOffset.create(queueName, ReadOffset.lastConsumed())
                    );
                    // 2、判断消息获取是否成功
                    if (ObjectUtils.isEmpty(list)) {
                        // 2.1、如果失败，说明没有消息继续下一轮循环
                        continue;
                    }
                    // 3、如果获取成功，开始下单
                    final MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> value = record.getValue();
                    final VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
                    // 4、处理订单
                    handlerVoucherOrder(voucherOrder);
                    // 5、 AKC确定
                    log.error(record.getId());
                    stringRedisTemplate.opsForStream().acknowledge(queueName, "g1", record.getId());
                } catch (Exception e) {
                    handlerPendingList();
                }
            }
        }

        private void handlerPendingList() {
            while (true) {
                try {
                    // 1、获取消息队列中的订单信息 XREADGROUP GROUP g1 c1 COUNT 1 STREAMS s1 0
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            // GROUP g1 c1
                            Consumer.from("g1", "c1"),
                            // COUNT 1
                            StreamReadOptions.empty().count(1),
                            // STREAMS s1 0
                            StreamOffset.create(queueName, ReadOffset.from("0"))
                    );
                    // 2、判断消息获取是否成功
                    if (ObjectUtils.isEmpty(list)) {
                        // 2.1、如果失败，说明没有消息继续下一轮循环
                        break;
                    }
                    // 3、如果获取成功，开始下单
                    final MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> value = record.getValue();
                    final VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
                    // 4、处理订单
                    handlerVoucherOrder(voucherOrder);
                    // 5、AKC确定
                    stringRedisTemplate.opsForStream().acknowledge(queueName, "g1", record.getId());
                } catch (Exception e) {
                    try {
                        TimeUnit.SECONDS.sleep(2L);
                    } catch (InterruptedException e1) {
                        log.error("处理订单异常----" + e.getMessage(), e);
                    }
                }
            }
        }
    }

    @PostConstruct
    private void init() {
        SEKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }

    private void handlerVoucherOrder(VoucherOrder voucherOrder) {
        final Long userId = voucherOrder.getUserId();
        final RLock lock = redissonClient.getLock("lock:order:" + userId);
        final boolean tryLock = lock.tryLock();
        try {
            if (!tryLock) {
                log.error("不容许重复下单");
                return;
            }
            proxy.createVoucherOrder(voucherOrder);
        } finally {
            lock.unlock();
        }

    }


    /**
     * 基于redis的stream
     * @param voucherId
     * @return
     */
    @Override
    public Result seckillVoucher(Long voucherId) {
        // 2.3 订单id
        final long orderId = redisWorker.nextId("order");
        // 1 执行脚本
        final Long userId = UserHolder.getUser().getId();
        final Long result = stringRedisTemplate.execute(UNLOCK_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),
                userId.toString(),String.valueOf(orderId));
        // 2 判断结果
        final int r = result.intValue();
        if (r != 0) {
            // 2.1 不为0，没有购买资格
            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
        }
        // 3：获取代理对象
        proxy = (IVoucherOrderService) AopContext.currentProxy();
        // 4 返回订单id
        return Result.ok(orderId);
    }

    @Override
    public Result createVoucherOrder(Long voucherId) {
        return null;
    }

    @Transactional
    @Override
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        // 一人一单
        final Long userId = voucherOrder.getUserId();
        final Long count = query().eq("user_id", userId)
                .eq("voucher_id", voucherOrder.getVoucherId()).count();
        if (count > 0) {
            log.error("您已经购买过一次啦！");
            return;
        }
        // 扣减库存
        final boolean success = iSeckillVoucherService.update().setSql("stock = stock-1")
                .eq("voucher_id", voucherOrder.getVoucherId())
                .gt("stock", 0)
                .update();
        if (!success) {
            log.error("库存不足！");
            return;
        }
        //6.4、入库
        save(voucherOrder);
    }


}
