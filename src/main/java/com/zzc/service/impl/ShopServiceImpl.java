package com.zzc.service.impl;

import cn.hutool.json.JSONUtil;
import com.zzc.entity.Shop;
import com.zzc.mapper.ShopMapper;
import com.zzc.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zzc.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.zzc.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 成哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Shop getFromCacheById(Long id) {
        // 1.根据id查redis缓存
        String shopKey = RedisConstants.CACHE_SHOP_PREFIX + id;
        String shopJson = stringRedisTemplate.opsForValue().get(shopKey);
        if (shopJson == null) {
            // 2.缓存不存在，查数据库，使用互斥锁防止缓存击穿
            String lockKey = RedisConstants.LOCK_SHOP_PREFIX + id;
            Shop shop = null;
            boolean gotLock = false;
            String lockValue = UUID.randomUUID() + "-" + Thread.currentThread().getId();
            try {
                gotLock = tryLock(lockKey, 10, lockValue);
                if (!gotLock) {
                    throw new RuntimeException("服务繁忙，请稍后重试！");
                }
                // ✅ 双重检查缓存，拿到锁后再查一次redis
                shopJson = stringRedisTemplate.opsForValue().get(shopKey);
                if (shopJson != null) {
                    if (shopJson.isEmpty()) {
                        return null;
                    }
                    return JSONUtil.toBean(shopJson, Shop.class);
                }
                // 缓存仍然为空，查询数据库
                shop = getById(id);
                if (shop != null) {
                    // 3.写入redis缓存，使用随机TTL防止缓存雪崩
                    stringRedisTemplate.opsForValue().set(shopKey, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL + (long) (Math.random() * 5), TimeUnit.MINUTES);
                } else {
                    // 4.防止缓存穿透，设置空值
                    stringRedisTemplate.opsForValue().set(shopKey, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                }
                return shop;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // 恢复中断
                throw new RuntimeException("运行时中断异常", e);
            } finally {
                // ✅ 只有成功拿到锁，才解锁
                if (gotLock) {
                    unLock(lockKey, lockValue);
                }
            }
        } else {
            // 6.缓存存在，判断是否是空值缓存
            if (shopJson.isEmpty()) {
                return null;
            }
        }
        return JSONUtil.toBean(shopJson, Shop.class);
    }

    /**
     * 自旋获取锁，传入唯一lockValue
     */
    private boolean tryLock(String lockKey, int retryTimes, String lockValue) throws InterruptedException {
        while (retryTimes > 0) {
            Boolean isLock = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, lockValue, LOCK_SHOP_TTL, TimeUnit.SECONDS);
            if (Boolean.TRUE.equals(isLock)) {
                return true;
            }
            Thread.sleep(50);
            retryTimes--;
        }
        return false;
    }

    /**
     * Lua原子解锁，判断锁归属，防止误删别人锁
     */
    private void unLock(String lockKey, String lockValue) {
        String luaScript = """
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('del', KEYS[1])
            else
                return 0
            end
            """;
        stringRedisTemplate.execute(
                new DefaultRedisScript<>(luaScript, Long.class),
                Collections.singletonList(lockKey),
                lockValue
        );
    }


    @Override
    public void updateShop(Shop shop) {
        // 1.先更新数据库
        updateById(shop);
        // 2.再删缓存
        String shopKey = RedisConstants.CACHE_SHOP_PREFIX + shop.getId();
        stringRedisTemplate.delete(shopKey);
    }
}
