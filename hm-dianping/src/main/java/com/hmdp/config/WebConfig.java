package com.hmdp.config;

import com.hmdp.Interceptor.LoginInterceptor;
import com.hmdp.Interceptor.RefreshInterceptor;
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
    @Autowired
    private RefreshInterceptor refreshInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 添加login拦截器
        registry.addInterceptor(loginInterceptor)
                .excludePathPatterns(   // 排除的请求
                        "/user/code",
                        "/user/login",
                        "/shop-type/**",
                        "/shop/**",
                        "/blog/hot"
                ).order(1);
        // Redis刷新拦截器,优先级要高于登录拦截器
        registry.addInterceptor(refreshInterceptor)
                .addPathPatterns("/**").order(0);
    }
}
