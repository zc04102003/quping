package com.hmdp.Interceptor;

import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 * @author : nongzhicong
 * 登录拦截器
 */
@Slf4j
@Component
public class LoginInterceptor implements HandlerInterceptor {
    /**
     * 登录拦截
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1.从请求中获取session
        HttpSession session = request.getSession();
        // 放行路径含'/login'的请求
//        String uri = request.getRequestURI();
//        if (uri.contains("/login")){
//            log.info("登录请求,放行");
//            return true;
//        }
        // 2.获取session中的用户
        Object userInfo = session.getAttribute("user");
        // 3.判断用户信息是否存在
        if (userInfo == null){
            // 4.不存在，拦截
            log.warn("当前用户不存在,拦截");
            // 响应401
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }
        // 5.存在，保存用户信息到ThreadLocal
        UserHolder.saveUser((UserDTO) userInfo);
        // 6.放行
        log.info("效验通过");
        return true;
    }

    /**
     * 请求处理完成后执行
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, @Nullable Exception ex) throws Exception {
        // 移除用户
        UserHolder.removeUser();
    }
}
