package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.SystemConstants;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 服务实现类
 * </p>
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    private final StringRedisTemplate stringRedisTemplate;
    @Autowired
    public UserServiceImpl(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /*
    * 发送手机验证码
    * 流程：
    *  1. 用utils包中的RegexUtils验证手机号是否合法
    *    1.1 如果不合法，直接返回失败结果
    *  2. 如果合法，使用hutool包中的RandomUtil生成6位随机验证码
    *  3. 将验证码保存在session中
    *  4. 模拟发送验证码（实际项目中会集成第三方短信服务商进行发送）
    * */
//    @Override
//    public void sendCode(String phone, HttpSession session) {
//        // 验证手机号是否合法
//        if (RegexUtils.isPhoneInvalid(phone)) {
//            throw new RuntimeException("手机号格式错误");
//        }
//        // 生成6位随机验证码
//        String code = RandomUtil.randomNumbers(6);
//        // 将验证码保存到session
//        session.setAttribute("code", code);
//        // 模拟发送验证码,实际项目中会集成第三方短信服务商进行发送
//        log.debug("发送短信验证码成功，验证码：{}", code);
//    }
    @Override
    public void sendCode(String phone) {
        // 验证手机号是否合法
        if (RegexUtils.isPhoneInvalid(phone)) {
            throw new RuntimeException("手机号格式错误");
        }
        // 生成6位随机验证码
        String code = RandomUtil.randomNumbers(6);
        // 将验证码保存到Redis
        String key = RedisConstants.LOGIN_CODE_KEY + phone;
        stringRedisTemplate.opsForValue().set(key,code, RedisConstants.LOGIN_CODE_TTL, TimeUnit.MINUTES);
        // 模拟发送验证码,实际项目中会集成第三方短信服务商进行
        log.debug("发送短信验证码成功，验证码：{}", code);
    }


    /*
    * 登录/注册功能
    * 流程：
    *  1. 校验手机号是否合法，虽然上一个接口已经校验过，但是主要是为了防止用户在获取验证码之后再次修改手机号
    *    1.1 如果不合法，直接返回失败结果
    *  2. 从session中获取保存的验证码，进行验证码校验
    *    2.1 如果验证码不一致，返回失败结果
    *  3. 如果一致，根据手机号查询用户是否存在：
    *    3.1 如果存在，则直接登录，保存用户信息到session中
    *    3.2 如果不存在，则注册新用户，并保存到数据库中，并且保存用户信息到session中
    *  4. 返回登录结果
    * */
//    @Override
//    public void login(LoginFormDTO loginForm, HttpSession session) {
//        // 校验手机号是否合法
//        String phone = loginForm.getPhone();
//        if (RegexUtils.isPhoneInvalid(phone)) {
//            throw new RuntimeException("手机号格式错误");
//        }
//        // 从session中获取保存的验证码，进行验证码校验
//        String code = loginForm.getCode();
//        String sessionAttribute = session.getAttribute("code").toString();
//          // 判断session中是否有验证码，可能存在验证码未发送
//        if (sessionAttribute == null) {
//            throw new RuntimeException("验证码不存在或已过期");
//        }
//        if (!code.equals(sessionAttribute)) {
//            throw new RuntimeException("验证码错误");
//        }
//        // 如果一致，根据手机号查询用户是否存在，使用MyBatis-Plus提供的lambdaQuery方法进行查询
//        User user = query().eq("phone", phone).one();
//          // 如果不存在，则注册新用户
//        if (user == null) {
//            user = User.builder()
//                    .phone(phone)
//                    .nickName(SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomString(phone,10)) // 随机生成昵称
//                    .build();
//            // 保存到数据库
//            save(user);
//        }
//        // 保存用户信息到session
//        session.setAttribute("user", user);
//    }
    @Override
    public String login(LoginFormDTO loginForm) {
        // 校验手机号是否合法
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            throw new RuntimeException("手机号格式错误");
        }
        // 从Redis中获取保存的验证码，进行验证码校验
        String code = loginForm.getCode();
        String key = RedisConstants.LOGIN_CODE_KEY + phone;
        // 注意，在获取验证码时，获取到的格式为String，但是带有前缀
        String redisCode = stringRedisTemplate.opsForValue().get(key);
        if (redisCode == null) {
            throw new RuntimeException("验证码不存在或已过期");
        }
        if (!code.equals(redisCode)) {
            throw new RuntimeException("验证码错误");
        }
        // 如果一致，根据手机号查询用户是否存在，使用MyBatis-Plus提供的lambdaQuery方法进行查询
        User user = query().eq("phone", phone).one();
        // 如果不存在，则注册新用户
        if (user == null) {
            user = User.builder()
                    .phone(phone)
                    .nickName(SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomString(phone,10)) // 随机生成昵称
                    .build();
            // 保存到数据库
            save(user);
        }
        // 保存用户信息到Redis，并返回token
        // 注意，这里使用hash类型保存用户信息
        String token = UUID.randomUUID().toString();
        String userKey = RedisConstants.LOGIN_USER_KEY + token;
        // 组织用户信息为Map
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(
                userDTO,
                new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((k, v) -> v == null ? null : v.toString())
        );
        log.debug("用户信息Map：{}", userMap);
        // 存储到Redis
        stringRedisTemplate.opsForHash().putAll(userKey, userMap);
        log.debug("用户信息存储到Redis成功，key：{}", userKey);
        // 设置过期时间，
        // 注意，在用户登录期间，每次访问都会续期，而不是简单的设置单个过期时间，需要在拦截器中实现
        stringRedisTemplate.expire(userKey, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);
        log.debug("用户信息Redis过期时间设置成功，key：{}", userKey);
        // 返回token，其实是返回key，通过这个key可以获取到用户信息
        return token;
    }

}
