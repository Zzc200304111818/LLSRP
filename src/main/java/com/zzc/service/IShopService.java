package com.zzc.service;

import com.zzc.dto.Result;
import com.zzc.entity.Shop;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 成哥
 * @since 2021-12-22
 */
public interface IShopService extends IService<Shop> {

    Shop getFromCacheById(Long id);
}
