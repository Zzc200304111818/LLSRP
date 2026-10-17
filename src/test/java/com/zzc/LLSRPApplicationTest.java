package com.zzc;

import com.zzc.service.impl.ShopServiceImpl;
import com.zzc.utils.RedisIDGenerator;
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
    private RedisIDGenerator redisIDGenerator;

    @Resource
    ExecutorService executorService;

    @Test
    public void test() throws InterruptedException {
        long begin = System.currentTimeMillis();
        CountDownLatch countDownLatch = new CountDownLatch(10);
        for (int i = 0; i < 10; i++) {
            executorService.submit(() -> {
                for (int j = 0; j < 1000; j++) {
                    long id = redisIDGenerator.nextID("order:");
                    System.out.println(id);
                }
                countDownLatch.countDown();
            });
        }
        countDownLatch.await();
        long end = System.currentTimeMillis();
        System.out.println("耗时：" + (end - begin));
    }
}
