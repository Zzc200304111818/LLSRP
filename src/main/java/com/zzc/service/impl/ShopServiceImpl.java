package com.zzc.service.impl;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.zzc.entity.Shop;
import com.zzc.mapper.ShopMapper;
import com.zzc.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zzc.utils.CacheClient;
import com.zzc.utils.RedisConstants;
import com.zzc.utils.RedisData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static com.zzc.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 成哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private ShopMapper shopMapper;

    @Resource
    private ExecutorService executorService;

    @Resource
    private CacheClient cacheClient;

    @PostConstruct
    public void cacheInit() {

    }

    /**
     * 缓存重建
     *
     * @param id
     * @param expireTime
     */
    public void saveShopToRedis(Long id, Long expireTime) {
        // 1.查询店铺数据
        Shop shop = shopMapper.selectById(id);

        // 2.封装数据到RedisData
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireTime));

        // 3.写入redis
        String shopKey = CACHE_SHOP_PREFIX + id;
        stringRedisTemplate.opsForValue().set(shopKey, JSONUtil.toJsonStr(redisData));
    }

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
                // 双重检查缓存，拿到锁后再查一次redis
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
                // 只有成功拿到锁，才解锁
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
/*        // 使用缓存逻辑过期
        // 1.查询缓存
        String shopKey = CACHE_SHOP_PREFIX + id;
        String shopJson = stringRedisTemplate.opsForValue().get(shopKey);
        if (shopJson == null) {
            // 2.缓存为空，返回
            return null;
        } else {
            // 3.非空，判断是否过期
            RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
            LocalDateTime expireTime = redisData.getExpireTime();
            JSONObject data = (JSONObject) redisData.getData();
            Shop shop = JSONUtil.toBean(data, Shop.class);
            if (expireTime.isAfter(LocalDateTime.now())) {
                // 4.缓存未过期，直接返回
                return shop;
            } else {
                // 5.缓存已过期，使用setNX互斥锁进行缓存重建
                String lockKey = LOCK_SHOP_PREFIX + id;
                String lockValue = UUID.randomUUID() + "-" + Thread.currentThread().getId();
                boolean isLock = false;
                isLock = Boolean.TRUE.equals(stringRedisTemplate.opsForValue().setIfAbsent(lockKey, lockValue, LOCK_SHOP_TTL, TimeUnit.SECONDS));
                if (isLock) {
                    // 6.二次检查：重新读取缓存，看看是否已经被其他线程重建
                    String newShopJson = stringRedisTemplate.opsForValue().get(shopKey);
                    if (newShopJson != null) {
                        RedisData newRedisData = JSONUtil.toBean(newShopJson, RedisData.class);
                        if (newRedisData.getExpireTime().isAfter(LocalDateTime.now())) {
                            // 缓存已被其他线程重建，直接返回新数据
                            JSONObject newData = (JSONObject) newRedisData.getData();
                            return JSONUtil.toBean(newData, Shop.class);
                        }
                    }
                    // 7.获取锁成功，异步线程重建缓存
                    executorService.submit(() -> {
                        try {
                            saveShopToRedis(id, CACHE_SHOP_TTL);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        } finally {
                            // 8.释放锁
                            unLock(lockKey, lockValue);
                        }
                    });
                }
                return shop;

            }
        }*/
        /*        return cacheClient.getWithCachePenetrate(CACHE_SHOP_PREFIX, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);*/
        /*        return cacheClient.getWithCacheBreakThrough(CACHE_SHOP_PREFIX, id, Shop.class, this::getById);*/
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
        String luaScript =
                "if redis.call('get', KEYS[1]) == ARGV[1] then\n" +
                        "    return redis.call('del', KEYS[1])\n" +
                        "else\n" +
                        "    return 0\n" +
                        "end";
        stringRedisTemplate.execute(
                new DefaultRedisScript<>(luaScript, Long.class),
                Collections.singletonList(lockKey),
                lockValue
        );
    }


    @Override
    @Transactional(rollbackFor = RuntimeException.class)
    public void updateShop(Shop shop) {
        // 1.先更新数据库
        updateById(shop);
        // 2.再删缓存
        String shopKey = RedisConstants.CACHE_SHOP_PREFIX + shop.getId();
        stringRedisTemplate.delete(shopKey);
    }
}
