package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import org.jetbrains.annotations.NotNull;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Autowired
    private ISeckillVoucherService iSeckillVoucherService;
    @Autowired
    private RedisWorker redisWorker;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    RedissonClient redissonClient;

    @Override
    public Result seckillVoucher(Long voucherId) {
        //1、查询优惠券
        final SeckillVoucher voucher = iSeckillVoucherService.getById(voucherId);
        //2、判断秒杀是否开始
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("秒杀未开始！");
        }
        //3、判断秒杀是否结束
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("秒杀已结束！");
        }
        //4、判断库存是否充足
        if (voucher.getStock() < 1) {
            return Result.fail("优惠券已经抢光了！");
        }
        final Long userId = UserHolder.getUser().getId();
        // 单体应用处理方式
//        synchronized (userId.toString().intern()) {
//            // 获取代理对象，避免事务失效
//            final IVoucherOrderService proxy = (IVoucherOrderService)AopContext.currentProxy();
//            return proxy.createVoucherOrder(voucherId);
//        }
//        SimpleRedisLock redisLock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
//        final boolean tryLock = redisLock.tryLock(1200L, TimeUnit.SECONDS);
        final RLock redisLock = redissonClient.getLock("lock:order:" + userId);
        final boolean tryLock = redisLock.tryLock();
        if(!tryLock) {
           // 获取锁失败，返回失败或重试，一人一单到场景直接返回失败
            return Result.fail("不容许重复下单！");
        }
        // 获取代理对象，避免事务失效
        try {
            final IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        } finally {
            redisLock.unlock();
        }

    }

    @Transactional
    public Result createVoucherOrder(Long voucherId) {
        // 一人一单
        final Long userId = UserHolder.getUser().getId();
        final Long count = query().eq("user_id", userId)
                .eq("voucher_id", voucherId).count();
        if (count > 0) {
            return Result.fail("您已经购买过一次啦！");
        }
        //5、扣减库存
        final boolean success = iSeckillVoucherService.update().setSql("stock = stock-1")
                .eq("voucher_id", voucherId)
                // 乐观锁解决超卖问题
//                .eq("stock", voucher.getStock())
                .gt("stock", 0)
                .update();
        if (!success) {
            return Result.fail("扣减库存失败");
        }
        //6、创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        //6.1、订单id
        final long orderId = redisWorker.nextId("order");
        voucherOrder.setId(orderId);
        //6.2、用户id
        voucherOrder.setUserId(userId);
        //6.3、代金劵id
        voucherOrder.setVoucherId(voucherId);
        //6.4、入库
        save(voucherOrder);

        //7、返回订单id
        return Result.ok(orderId);
    }

}
