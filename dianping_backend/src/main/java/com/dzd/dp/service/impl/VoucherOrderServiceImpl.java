package com.dzd.dp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.dzd.dp.dto.Result;
import com.dzd.dp.entity.VoucherOrder;
import com.dzd.dp.mapper.VoucherOrderMapper;
import com.dzd.dp.service.ISeckillVoucherService;
import com.dzd.dp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dzd.dp.utils.RedisIdWorker;
import com.dzd.dp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
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

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Autowired
    private ISeckillVoucherService seckillVoucherService;

    @Autowired
    private StringRedisTemplate template;

    @Autowired
    private RedisIdWorker redisIdWorker;

    @Autowired
    private RedissonClient redissonClient;

    //线程池
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();
    //线程任务
    private IVoucherOrderService proxy;
    //从消息队列获取消息
    private class VoucherOrderHandler implements Runnable{
        private String queueName = "stream.orders";
        @Override
        public void run() {
            while (true){
                //获取队列中的订单信息
                //xreadgroup group g1 c1 count 1 block 2000 streams stream.orders >
                try {
                    List<MapRecord<String, Object, Object>> list = template.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(queueName, ReadOffset.lastConsumed())
                    );
                    //判断消息获取是否成功
                    if(list==null || list.isEmpty()){
                        //如果不成功，说明没有消息，继续下一次循环
                        continue;
                    }
                    //成功就下单
                    //创建订单
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> values = record.getValue();
                    VoucherOrder order = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), true);
                    handleVoucherOrder(order);
                    //ACK确认
                    //xack stream.orders g1 id
                    template.opsForStream().acknowledge(queueName,"g1",record.getId());
                } catch (Exception e) {
                    log.error("处理订单异常",e);
                    //没确认，进入pending_list
                    handlePendingList();
                }
            }
        }

        private void handlePendingList() {
            while (true){
                //获取pending队列中的订单信息
                //xreadgroup group g1 c1 count 1 streams stream.orders 0
                try {
                    List<MapRecord<String, Object, Object>> list = template.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1),
                            StreamOffset.create(queueName, ReadOffset.from("0"))
                    );
                    //判断消息获取是否成功
                    if(list==null || list.isEmpty()){
                        //如果不成功，说明pending_list没有消息，结束
                        break;
                    }
                    //成功就下单
                    //创建订单
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> values = record.getValue();
                    VoucherOrder order = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), true);
                    handleVoucherOrder(order);
                    //ACK确认
                    //xack stream.orders g1 id
                    template.opsForStream().acknowledge(queueName,"g1",record.getId());
                } catch (Exception e) {
                    log.error("处理pending_list订单异常",e);
                    //没确认，进入pending_list
                }
            }
        }
    }


    /*private BlockingQueue<VoucherOrder> orderQueue = new ArrayBlockingQueue<>(1024*1024);
    private class VoucherOrderHandler implements Runnable{
        @Override
        public void run() {
            while (true){
                //获取队列中的订单信息
                try {
                    VoucherOrder order = orderQueue.take();
                    //创建订单
                    handleVoucherOrder(order);
                } catch (Exception e) {
                    log.error("处理订单异常",e);
                }
            }
        }
    }*/

    private void handleVoucherOrder(VoucherOrder order) {
        Long userId = order.getUserId();
        RLock lock = redissonClient.getLock("lock:order:"+userId);
        boolean isLock = lock.tryLock();
        if (!isLock){
            log.error("不允许重复下单");
            return;
        }
        try {
            proxy.createVoucherOrder(order);
        } finally {
            lock.unlock();
        }

    }

    //让任务在初始化完毕就进行
    @PostConstruct
    private void init(){
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }
    private static final DefaultRedisScript<Long> SECKILL_QUERY;
    static final String SECKILL_LUA = "seckill.lua";
    static {
        SECKILL_QUERY = new DefaultRedisScript<>();
        SECKILL_QUERY.setLocation(new ClassPathResource(SECKILL_LUA));
        SECKILL_QUERY.setResultType(Long.class);
    }


    @Override
    public Result secKillVoucher(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        long orderId = redisIdWorker.nextId("order");
        //lua脚本查询优惠券
        Long res = template.execute(
                SECKILL_QUERY,
                Collections.emptyList(),
                voucherId.toString(), userId.toString(), String.valueOf(orderId)
        );
        //判断结果是否为0
        if (res.intValue() != 0){
            //不为0，代表没有购买资格
            return Result.fail(res.intValue()==1?"库存不足":"不能重复下单");
        }
        //事务由spring管理，spring通过代理对象来实现事务
        //获取代理对象(事务)
        proxy = (IVoucherOrderService) AopContext.currentProxy();
        return Result.ok(orderId);
    }
    /*@Override
    public Result secKillVoucher(Long voucherId) {
        Long userId = UserHolder.getUser().getId();

        //lua脚本查询优惠券
        Long res = template.execute(
                SECKILL_QUERY,
                Collections.emptyList(),
                voucherId.toString(), userId.toString()
        );
        //判断结果是否为0
        if (res.intValue() != 0){
            //不为0，代表没有购买资格
            return Result.fail(res.intValue()==1?"库存不足":"不能重复下单");
        }
        //为0，有购买资格，需要保存下单信息到阻塞队列
        //TODO
        VoucherOrder voucherOrder = new VoucherOrder();
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setUserId(userId);
        voucherOrder.setId(orderId);
        //创建阻塞队列
        orderQueue.add(voucherOrder);
        //事务由spring管理，spring通过代理对象来实现事务
        //获取代理对象(事务)
        proxy = (IVoucherOrderService) AopContext.currentProxy();
        return Result.ok(orderId);
    }*/

    /*@Override
    public Result secKillVoucher(Long voucherId) {
        //查询优惠券
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        //秒杀是否开始，且库存充足才能下单
        LocalDateTime beginTime = voucher.getBeginTime();
        LocalDateTime endTime = voucher.getEndTime();
        if (beginTime.isAfter(LocalDateTime.now())) {
            return Result.fail("秒杀尚未开始");
        }
        if (endTime.isBefore(LocalDateTime.now())) {
            return Result.fail("秒杀已经结束");
        }
        if (voucher.getStock()<1) {
            return Result.fail("库存不足");
        }

        Long userId = UserHolder.getUser().getId();
        //分布式锁
        //获取锁对象
        //SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, template);
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        boolean isLock = lock.tryLock();
        if (!isLock){
            return Result.fail("不允许重复下单");
        }

        try {
            //事务由spring管理，spring通过代理对象来实现事务
            //获取代理对象(事务)
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        } catch (IllegalStateException e) {
            throw new RuntimeException(e);
        } finally {
            lock.unlock();
        }

//        synchronized (userId.toString().intern()){
//            //事务由spring管理，spring通过代理对象来实现事务
//            //获取代理对象(事务)
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//            return proxy.createVoucherOrder(voucherId);
//        }
    }*/

    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        //一人一单，悲观锁限制一单
        Long userId = voucherOrder.getUserId();
        Long voucherId = voucherOrder.getVoucherId();
        Integer count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        if (count > 0){
            log.error("不允许重复下单");
            return;
        }

        //扣除库存，乐观锁防止超卖
        seckillVoucherService.updateStock(voucherId);
        save(voucherOrder);

    }

}
