package com.zzc.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.zzc.entity.SeckillVoucher;
import com.zzc.entity.VoucherOrder;
import com.zzc.mapper.SeckillVoucherMapper;
import com.zzc.mapper.VoucherOrderMapper;
import com.zzc.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zzc.utils.RedisIdGenerator;
import com.zzc.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 成哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Resource
    private SeckillVoucherMapper seckillVoucherMapper;

    @Resource
    private RedisIdGenerator redisIdGenerator;

    @Resource
    private VoucherOrderMapper voucherOrderMapper;

    @Override
    @Transactional
    public Long seckillVoucher(Long voucherId) {
        // 1.查询优惠券
        SeckillVoucher seckillVoucher = seckillVoucherMapper.selectById(voucherId);
        // 2.判断优惠券是否存在
        if (seckillVoucher == null) {
            return null;
        }
        // 3.判断是否在秒杀期间
        LocalDateTime beginTime = seckillVoucher.getBeginTime();
        LocalDateTime endTime = seckillVoucher.getEndTime();
        LocalDateTime now = LocalDateTime.now();
        if (beginTime.isAfter(now)) {
            throw new RuntimeException("秒杀还未开始！");
        }
        if (endTime.isBefore(now)) {
            throw new RuntimeException("秒杀已经结束！");
        }
        // 4.判断库存是否充足
        Integer stock = seckillVoucher.getStock();
        if (stock == null || stock < 1) {
            throw new RuntimeException("库存不足！");
        }
        // 5.扣减库存（原子更新，判断和更新放在同一条Sql）
        LambdaUpdateWrapper<SeckillVoucher> updateWrapper = new LambdaUpdateWrapper<SeckillVoucher>()
                .setSql("stock = stock - 1")
                .eq(SeckillVoucher::getVoucherId, voucherId)
                .ge(SeckillVoucher::getStock, 1);
        seckillVoucherMapper.update(null, updateWrapper);
        // 6.创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        // 订单id
        Long orderId = redisIdGenerator.nextId("order:");
        voucherOrder.setId(orderId);
        // 用户id
        voucherOrder.setUserId(UserHolder.getUser().getId());
        // 代金券id
        voucherOrder.setVoucherId(voucherId);
        // 7.插入订单
        voucherOrderMapper.insert(voucherOrder);
        return orderId;
    }
}
