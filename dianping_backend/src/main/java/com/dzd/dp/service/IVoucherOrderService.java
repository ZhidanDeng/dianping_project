package com.dzd.dp.service;

import com.dzd.dp.dto.Result;
import com.dzd.dp.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IVoucherOrderService extends IService<VoucherOrder> {

    Result secKillVoucher(Long voucherId);
    void createVoucherOrder(VoucherOrder order);
}
