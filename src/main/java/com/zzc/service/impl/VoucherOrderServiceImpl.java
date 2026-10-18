package com.zzc.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.zzc.entity.SeckillVoucher;
import com.zzc.entity.VoucherOrder;
import com.zzc.mapper.SeckillVoucherMapper;
import com.zzc.mapper.VoucherOrderMapper;
import com.zzc.service.ISeckillVoucherService;
import com.zzc.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zzc.utils.RedisIdGenerator;
import com.zzc.utils.RedisLockUtils;
import com.zzc.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.UUID;


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
    private static final String LOCK_PREFIX = "lock:";

    @Resource
    private SeckillVoucherMapper seckillVoucherMapper;

    @Resource
    private RedisIdGenerator redisIdGenerator;

    @Resource
    private VoucherOrderMapper voucherOrderMapper;

    @Resource
    private RedisLockUtils redisLockUtils;
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Long seckillVoucher(Long voucherId) {
        // 1.查询优惠券
        SeckillVoucher seckillVoucher = seckillVoucherMapper.selectById(voucherId);
        // 2.判断优惠券是否存在
        if (seckillVoucher == null) {
            throw new RuntimeException("优惠券不存在！");
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
        Long userId = UserHolder.getUser().getId();
/*        // 单机本地锁可保证并发安全
        synchronized (userId.toString().intern()) {
            return ((IVoucherOrderService) AopContext.currentProxy()).doSeckill(voucherId, userId);
        }*/
        String lockKey = LOCK_PREFIX + userId;
        String value = UUID.randomUUID() + "-" + Thread.currentThread().getId();
        // 加分布式锁
        boolean isLock = redisLockUtils.tryLock(lockKey, value, 1200);
        if (!isLock) {
            // 直接返回
            throw new RuntimeException("不允许重复下单！");
        }
        try {
            return ((IVoucherOrderService) AopContext.currentProxy()).doSeckill(voucherId, userId);
        } catch (RuntimeException e) {
            throw new RuntimeException(e);
        } finally {
            redisLockUtils.unlock(lockKey, value);
        }
    }

    @Transactional
    @Override
    public Long doSeckill(Long voucherId, Long userId) {
        // 5.查重逻辑（防止用户隔几秒重复点击）
        int count = voucherOrderMapper.selectCount(new LambdaQueryWrapper<VoucherOrder>()
                .eq(VoucherOrder::getUserId, userId)
                .eq(VoucherOrder::getVoucherId, voucherId));
        if (count > 0) {
            throw new RuntimeException("您已经购买过该优惠券！");
        }
        // 6.扣减库存（原子更新，判断和更新放在同一条Sql）
        LambdaUpdateWrapper<SeckillVoucher> updateWrapper = new LambdaUpdateWrapper<SeckillVoucher>()
                .setSql("stock = stock - 1")
                .eq(SeckillVoucher::getVoucherId, voucherId)
                .ge(SeckillVoucher::getStock, 1);
        int row = seckillVoucherMapper.update(null, updateWrapper);
        if (row == 0) {
            throw new RuntimeException("库存不足！");
        }
        // 7.创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        // 订单id
        Long orderId = redisIdGenerator.nextId("order:");
        voucherOrder.setId(orderId);
        // 用户id
        voucherOrder.setUserId(userId);
        // 代金券id
        voucherOrder.setVoucherId(voucherId);
        // 8.插入订单
        voucherOrderMapper.insert(voucherOrder);
        return orderId;
    }
}
