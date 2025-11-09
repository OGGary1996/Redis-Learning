package com.hmdp.config;

import com.hmdp.interceptor.LoginInterceptor;
import com.hmdp.interceptor.RefreshTokenInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class MvcConfig implements WebMvcConfigurer {
    // 注入StringRedisTemplate，用于操作Redis
    private final StringRedisTemplate stringRedisTemplate;
    @Autowired
    public MvcConfig(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    // 添加拦截器的配置
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 注册登录拦截器
        // 1. 注册刷新token拦截器，拦截所有请求,order = 0，优先级最高
        registry.addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate))
                .addPathPatterns("/**")// 拦截所有请求
                .order(0);
        // 2. 注册登录拦截器，拦截需要登录才能访问的请求,order = 1
        registry.addInterceptor(new LoginInterceptor())
                .excludePathPatterns(// 排除不需要拦截的路径
                        "/user/code",    // 发送验证码
                        "/user/login",   // 登录
                        "/shop/**",      // 店铺相关接口
                        "/shop-type/**", // 店铺类型相关接口
                        "/voucher/**",   // 代金券相关接口
                        "/upload/**",    // 上传相关接口
                        "/blog/hot"      // 热门博客
                )
                .order(1);
    }
}
