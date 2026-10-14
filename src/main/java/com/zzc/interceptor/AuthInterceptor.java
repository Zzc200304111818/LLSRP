package com.zzc.interceptor;

import cn.hutool.core.bean.BeanUtil;
import com.zzc.dto.UserDTO;
import com.zzc.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.zzc.utils.RedisConstants.*;

@Slf4j
@Component
public class AuthInterceptor implements HandlerInterceptor {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
/*        // 1.获取session
        HttpSession session = request.getSession(false);
        if (session != null) {
            // 2.解析session
            Object user = session.getAttribute("user");
            if (user != null) {
                // 3.用户存在，保存到ThreadLocal
                UserHolder.saveUser((UserDTO) user);
                log.info("SESSION有效，用户：{}", user);
                return true;
            }
        }*/
        // 1.获取token
        String token = request.getHeader("authorization");
        log.debug("token:{}", token);
        if (token != null) {
            // 2.根据token从redis获取用户信息
            String userKey = LOGIN_USER_PREFIX + token;
            Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(userKey);
            if (!userMap.isEmpty()) {
                // 3.用户存在，将用户信息保存到ThreadLocal
                UserDTO user = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
                UserHolder.saveUser(user);
                // 4.token续期
                stringRedisTemplate.expire(userKey, LOGIN_USER_TTL, TimeUnit.MINUTES);
                log.debug("{} 放行，用户 {}", request.getRequestURI(), user);
                return true;
            }
        }
        response.setStatus(401);
        return false;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 清除ThreadLocal，防止内存泄漏
        UserHolder.removeUser();
    }
}
