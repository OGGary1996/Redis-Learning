package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import jakarta.servlet.http.HttpSession;

/**
 * <p>
 *  服务类
 * </p>
 */
public interface IUserService extends IService<User> {

    /*
    * 发送手机验证码
    * 流程：
    *  1. 用utils包中的RegexUtils验证手机号是否合法
    *    1.1 如果不合法，直接返回失败结果
    *  2. 如果合法，使用hutool包中的RandomUtil生成6位随机验证码
    *  3. 将验证码保存在session中
    *  4. 模拟发送验证码（实际项目中会集成第三方短信服务商进行发送）
    * */
//    void sendCode(String phone, HttpSession session);
    void sendCode(String phone);

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
//    void login(LoginFormDTO loginForm, HttpSession session);
    String login(LoginFormDTO loginForm);

    /*
    * 查询id所在用户简介页面用于显示Blog和共同关注列表
    * */
    UserDTO queryByUserId(Long id);
}
