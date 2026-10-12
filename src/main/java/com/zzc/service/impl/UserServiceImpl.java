package com.zzc.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zzc.entity.User;
import com.zzc.mapper.UserMapper;
import com.zzc.service.IUserService;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 成哥
 * @since 2021-12-22
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

}
