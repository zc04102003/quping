package com.hmdp.config;

import com.hmdp.Interceptor.LoginInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * @author nongzhicong
 * @date 2026/1/14
 * 拦截器配置类
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Autowired
    private LoginInterceptor loginInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 添加拦截器
        registry.addInterceptor(loginInterceptor)
                .addPathPatterns("/**")     // 拦截所有请求
                .excludePathPatterns(   // 排除的请求
                        "/user/code",
                        "/user/login",
                        "/shop-type/**",
                        "/shop/**",
                        "/blog/hot"
                );
    }
}
