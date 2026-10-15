package com.zzc;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.zzc.entity.Shop;
import com.zzc.service.impl.ShopServiceImpl;
import com.zzc.utils.RedisConstants;
import com.zzc.utils.RedisData;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;

@SpringBootTest
public class LLSRPApplicationTest {
    @Resource
    private ShopServiceImpl shopServiceImpl;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Test
    public void test() throws InterruptedException {
        shopServiceImpl.saveShopToRedis(1L, 10L);
        String s = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_PREFIX + 1);
        RedisData redisData = JSONUtil.toBean(s, RedisData.class);
        JSONObject data = (JSONObject) redisData.getData();
        Shop shop = JSONUtil.toBean(data, Shop.class);
        System.out.println(shop.getName());
    }
}
