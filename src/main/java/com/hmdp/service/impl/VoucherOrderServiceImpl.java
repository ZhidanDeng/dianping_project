package com.hmdp.service.impl;

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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.util.Collections;
import java.util.concurrent.*;

/**
 * redis判断
 *
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

    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);

    private ExecutorService SEKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();
    private IVoucherOrderService proxy;

    private class VoucherOrderHandler implements Runnable {
        @Override
        public void run() {
            while (true) {
                try {
                    // 获取订单
                    final VoucherOrder voucherOrder = orderTasks.take();
                    // 处理订单
                    handlerVoucherOrder(voucherOrder);
                } catch (InterruptedException e) {
                    log.error("处理订单异常！");
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

    @Override
    public Result seckillVoucher(Long voucherId) {
        // 1 执行脚本
        final Long userId = UserHolder.getUser().getId();
        final Long result = stringRedisTemplate.execute(UNLOCK_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),
                userId.toString());
        // 2 判断结果
        final int r = result.intValue();
        if (r != 0) {
            // 2.1 不为0，没有购买资格
            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
        }
        // 2.2 有购买资格，把下单信息保存到阻塞队列
        VoucherOrder voucherOrder = new VoucherOrder();
        // 2.3 订单id
        final long orderId = redisWorker.nextId("order");
        voucherOrder.setId(orderId);
        // 2.4 代金券id
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setUserId(userId);
        // 2.5 保存到阻塞队列
        orderTasks.add(voucherOrder);
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
