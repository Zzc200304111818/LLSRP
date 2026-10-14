package com.zzc.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zzc.entity.ShopType;
import com.zzc.mapper.ShopTypeMapper;
import com.zzc.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zzc.utils.RedisConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 成哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Resource
    private ShopTypeMapper shopTypeMapper;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public List<ShopType> getTypeListFromCache() {
        // 1.先查缓存
        String shopTypesJSON = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOPTYPES);
        if (shopTypesJSON == null) {
            // 2.缓存不存在，查数据库
            QueryWrapper<ShopType> queryWrapper = new QueryWrapper<>();
            queryWrapper.orderByAsc("sort");
            List<ShopType> shopTypeList = shopTypeMapper.selectList(queryWrapper);
            if (!shopTypeList.isEmpty()) {
                // 3.非空，写入redis缓存
                stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOPTYPES, JSONUtil.toJsonStr(shopTypeList));
            }
            // 4.返回
            return shopTypeList;
        }
        log.debug("店铺列表：{}", shopTypesJSON);
        // 5.缓存非空
        return JSONUtil.toList(shopTypesJSON, ShopType.class);
    }
}
