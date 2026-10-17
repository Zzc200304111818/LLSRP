package com.zzc;

import com.zzc.service.impl.ShopServiceImpl;
import com.zzc.utils.RedisIdGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.Scanner;
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

    @Test
    public void test() throws InterruptedException {

    }
}
