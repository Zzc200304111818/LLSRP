package com.zzc.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zzc.dto.Result;
import com.zzc.entity.User;
import com.zzc.mapper.UserMapper;
import com.zzc.service.IUserService;
import com.zzc.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

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
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Override
    public Result sendCode(String phone, HttpSession session) {
        // 1.校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            // 2.非法，返回fail
            Result.fail("手机号格式错误！");
        }
        // 3.合法，生成短信验证码
        String code = RandomUtil.randomNumbers(6);
        // 4.模拟短信发送
        log.debug("手机号 {} 已发送验证码：{}", phone, code);
        // 5.session存储验证码进行登录校验
        session.setAttribute("code", code);
        // 6.返回ok
        return Result.ok();
    }
}
