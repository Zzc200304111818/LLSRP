package com.zzc.interceptor;

import com.zzc.dto.UserDTO;
import com.zzc.entity.User;
import com.zzc.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

@Slf4j
@Component
public class SessionInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1.获取session
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
