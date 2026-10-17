package com.zzc;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zzc.entity.SeckillVoucher;
import com.zzc.mapper.SeckillVoucherMapper;
import com.zzc.service.impl.ShopServiceImpl;
import com.zzc.utils.RedisIdGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;

@SpringBootTest
public class LLSRPApplicationTest {
    @Resource
    private ShopServiceImpl shopServiceImpl;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedisIdGenerator redisIDGenerator;

    @Resource
    ExecutorService executorService;

    @Resource
    private SeckillVoucherMapper seckillVoucherMapper;

    @Test
    public void test() throws InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(100);
        for (int i = 0; i < 100; i++) {
            executorService.submit(() -> {
                LambdaUpdateWrapper<SeckillVoucher> updateWrapper = new LambdaUpdateWrapper<SeckillVoucher>()
                        .setSql("stock = stock - 1")
                        .eq(SeckillVoucher::getVoucherId, 2)
                        .ge(SeckillVoucher::getStock, 1);
                seckillVoucherMapper.update(null, updateWrapper);
                System.out.println(Thread.currentThread().getId() + " decrease 1");
                countDownLatch.countDown();
            });
        }
        countDownLatch.await();
    }
}
