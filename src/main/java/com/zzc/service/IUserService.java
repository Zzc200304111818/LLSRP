package com.zzc.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.zzc.dto.LoginFormDTO;
import com.zzc.dto.Result;
import com.zzc.entity.User;

import javax.servlet.http.HttpSession;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 成哥
 * @since 2021-12-22
 */
public interface IUserService extends IService<User> {

    Result sendCode(String phone);

    Result login(LoginFormDTO loginForm);
}
