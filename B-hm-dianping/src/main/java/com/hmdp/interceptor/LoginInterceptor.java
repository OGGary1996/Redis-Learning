package com.hmdp.interceptor;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import java.util.Map;
import java.util.concurrent.TimeUnit;

public class LoginInterceptor implements HandlerInterceptor {
//    private final StringRedisTemplate stringRedisTemplate;
//    public LoginInterceptor(StringRedisTemplate stringRedisTemplate) {
//        this.stringRedisTemplate = stringRedisTemplate;
//    }

//    @Override
//    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
//        // 获取session
//        HttpSession session = request.getSession();
//        // 判断session中是否有用户信息
//        User user = (User) session.getAttribute("user");
//        // 如果没有，说明未登录，返回false，拦截请求
//        if (user == null){
//            response.setStatus(401);
//            return false;
//        }
//        // 如果有，说明已登录，放行请求，并且将用户信息保存到ThreadLocal中
//        // 使用utils包中的UserHolder保存用户信息
//        // 注意： 这里不能直接保存User对象，因为User对象可能包含敏感信息，比如密码等，所以UserHolder中保存的是UserDTO对象，隐藏了敏感信息
//        UserDTO userDTO = new UserDTO();
//        BeanUtils.copyProperties(user, userDTO);
//        UserHolder.saveUser(userDTO);
//        return true;
//    }
//    @Override
//    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
//        // 从请求头中获取token
//        String token = request.getHeader("authorization");
//        // 如果tokenKey都是空，则说明token过期了，返回false，拦截请求
//        if (token == null) {
//            response.setStatus(401);
//            return false;
//        }
//        // 基于token，从Redis中获取用户信息
//        String userKey = RedisConstants.LOGIN_USER_KEY + token;
//        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(userKey);
//        // 如果获取不到，说明未登录，返回false，拦截请求
//        if (userMap.isEmpty()) {
//            response.setStatus(401);
//            return false;
//        }
//        // 如果获取到，说明已登录，放行请求，并且将用户信息保存到ThreadLocal中
//        UserDTO userDTO = new UserDTO();
//        BeanUtil.fillBeanWithMap(userMap,userDTO,false);
//        UserHolder.saveUser(userDTO);
//        // 注意: 需要刷新token的有效期，如果不刷新，用户在使用过程中可能会因为token过期而被迫重新登录
//        stringRedisTemplate.expire(userKey, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);
//        return true;
//    }


    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 只需要判断ThreadLocal中是否有用户信息即可
        if (UserHolder.getUser() == null) {
            response.setStatus(401);
            return false;
        }
        return true;
    }
}
