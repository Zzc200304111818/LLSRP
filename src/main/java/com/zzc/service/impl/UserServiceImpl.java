package com.zzc.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zzc.dto.LoginFormDTO;
import com.zzc.dto.Result;
import com.zzc.entity.User;
import com.zzc.mapper.UserMapper;
import com.zzc.service.IUserService;
import com.zzc.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import static net.sf.jsqlparser.util.validation.metadata.NamedObject.user;

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
    @Resource
    private UserMapper userMapper;

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
        log.info("手机号 {} 已发送验证码：{}", phone, code);
        // 5.session存储验证码进行登录校验
        session.setAttribute("code", code);
        // 6.返回ok
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        // 1.解析参数
        String phone = loginForm.getPhone();
        String password = loginForm.getPassword();
        String code = loginForm.getCode();
        // 2.校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            // 3.非法，返回fail
            Result.fail("手机号格式错误！");
        }
        // 4.校验验证码
        Object cacheCode = session.getAttribute("code");
        if (RegexUtils.isCodeInvalid(code) || !code.equals(cacheCode)) {
            // 5.非法，返回fail
            return Result.fail("验证码错误！");
        }
        // 6.查询数据库此手机号是否已注册
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("phone", phone);
        User user = userMapper.selectOne(queryWrapper);
        if (user == null) {
            // 7.用户不存在进行注册
            user = new User();
            user.setPhone(phone);
            user.setNickName("user_" + RandomUtil.randomString(10));
            user.setPassword(password);
            userMapper.insert(user);
        }
        // 8.保存用户信息到session
        session.setAttribute("user", user);
        return Result.ok("登录成功");
    }
}
