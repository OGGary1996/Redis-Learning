package com.hmdp.controller;


import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.UserInfo;
import com.hmdp.service.IUserInfoService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpSession;

/**
 * <p>
 * 前端控制器
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/user")
public class UserController {

    private final IUserService userService;
    private final IUserInfoService userInfoService;
    @Autowired
    public UserController(IUserService userService, IUserInfoService userInfoService) {
        this.userService = userService;
        this.userInfoService = userInfoService;
    }


    /**
     * 发送手机验证码
     * 通过Session实现
     */
//    @PostMapping("/code")
//    public Result sendCode(@RequestParam("phone") String phone, HttpSession session) {
//        log.info("发送短信验证码，手机号：{}", phone);
//        userService.sendCode(phone, session);
//        return Result.ok();
//    }
    /*
    * 发送手机验证码
    * 通过Redis实现
    * */
    @PostMapping("/code")
    public Result sendCode(@RequestParam("phone") String phone){
        log.info("发送短信验证码，手机号：{}", phone);
        userService.sendCode(phone);
        return Result.ok();
    }


    /**
     * 登录功能
     * @param loginForm 登录参数，包含手机号、验证码；或者手机号、密码
     * 通过Session实现
     */
//    @PostMapping("/login")
//    public Result login(@RequestBody LoginFormDTO loginForm, HttpSession session){
//        log.info("用户登录，参数：{}", loginForm);
//        userService.login(loginForm, session);
//        return Result.ok();
//    }
    /**
     * 登录功能
     * @param loginForm 登录参数，包含手机号、验证码；或者手机号、密码
     * 通过Redis实现
     */
    @PostMapping("/login")
    public Result login(@RequestBody LoginFormDTO loginForm){
        log.info("用户登录，参数：{}", loginForm);
        String token = userService.login(loginForm);
        return Result.ok(token);
    }


    /**
     * 登出功能
     * @return 无
     */
    @PostMapping("/logout")
    public Result logout(){
        // TODO 实现登出功能
        return Result.fail("功能未完成");
    }

    @GetMapping("/me")
    public Result me(){
        // 直接从ThreadLocal中获取当前登录的用户并返回
        UserDTO user = UserHolder.getUser();
        return Result.ok(user);
    }

    @GetMapping("/info/{id}")
    public Result info(@PathVariable("id") Long userId){
        // 查询详情
        UserInfo info = userInfoService.getById(userId);
        if (info == null) {
            // 没有详情，应该是第一次查看详情
            return Result.ok();
        }
        info.setCreateTime(null);
        info.setUpdateTime(null);
        // 返回
        return Result.ok(info);
    }

    /*
    * 查询id所在用户简介页面用于显示Blog和共同关注列表
    * */
    @GetMapping("/{id}")
    public Result queryByUserId(@PathVariable("id") Long id) {
        UserDTO userDTO = userService.queryByUserId(id);
        return Result.ok(userDTO);
    }
}
