package com.dzd.dp.service.impl;

import com.dzd.dp.entity.SeckillVoucher;
import com.dzd.dp.mapper.SeckillVoucherMapper;
import com.dzd.dp.service.ISeckillVoucherService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 秒杀优惠券表，与优惠券是一对一关系 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2022-01-04
 */
@Service
public class SeckillVoucherServiceImpl extends ServiceImpl<SeckillVoucherMapper, SeckillVoucher> implements ISeckillVoucherService {


    @Override
    public void updateStock(Long voucherId) {
        getBaseMapper().updateStock(voucherId);
    }
}
