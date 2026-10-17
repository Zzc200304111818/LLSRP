package com.zzc.service;

import com.zzc.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 成哥
 * @since 2021-12-22
 */
public interface IVoucherOrderService extends IService<VoucherOrder> {

    Long seckillVoucher(Long voucherId);

    Long doSeckill(Long voucherId, Long userId);
}
