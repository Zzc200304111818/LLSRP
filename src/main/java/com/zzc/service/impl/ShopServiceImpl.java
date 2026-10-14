package com.zzc.service.impl;

import cn.hutool.json.JSONUtil;
import com.zzc.entity.Shop;
import com.zzc.mapper.ShopMapper;
import com.zzc.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zzc.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

import static com.zzc.utils.RedisConstants.CACHE_NULL_TTL;
import static com.zzc.utils.RedisConstants.CACHE_SHOP_TTL;

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
            // 2.缓存不存在，查数据库
            Shop shop = getById(id);
            if (shop != null) {
                // 3.写入redis缓存
                stringRedisTemplate.opsForValue().set(shopKey, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL + (long) (Math.random() * 5), TimeUnit.MINUTES);
            }
            // 4.防止缓存穿透，设置空值
            stringRedisTemplate.opsForValue().set(shopKey, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return shop;
        }
        // 5.判断是否是空值缓存
        if (shopJson.isEmpty()) {
            return null;
        }
        return JSONUtil.toBean(shopJson, Shop.class);
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
