package com.hmdp.interceptor;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;

public class RefreshTokenInterceptor implements HandlerInterceptor {
    private final StringRedisTemplate stringRedisTemplate;
    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /*
    * 主要作用：
    *  1. 拦截所有的请求
    *  2. 在所有的请求中，都获取请求头中的token，并且进行验证
    *  3. 如果token合法，则刷新token的有效期
    *  4. 如果token不合法，则直接放行，让后续的拦截器进行处理
    * */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 从请求头中获取token
        String token = request.getHeader("authorization");
        // 如果tokenKey都是空，要么是访客登录，要么是token过期了，直接放行，让后续的拦截器针对不同路径（访客登录或者实效）进行处理
        if (token == null) {
            return true;
        }
        // 获取用户信息
        String userKey = "login:token:" + token;
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(userKey);
        // 如果获取不到，说明token无效，直接放行，让后续的拦截器针对不同路径（访客登录或者实效）进行处理
        if (userMap.isEmpty()) {
            return true;
        }
        // 如果不是空，说明token合法
        // 存入ThreadLocal
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(),false);
        UserHolder.saveUser(userDTO);
        // 刷新token的有效期
        stringRedisTemplate.expire(userKey, RedisConstants.LOGIN_USER_TTL, java.util.concurrent.TimeUnit.MINUTES);
        // 放行
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 移除ThreadLocal中的用户信息，防止内存泄漏
        UserHolder.removeUser();
    }
}
