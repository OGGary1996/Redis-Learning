package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.service.IFollowService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * <p>
 *  前端控制器
 * </p>
 */
@RestController
@RequestMapping("/follow")
public class FollowController {

    private final IFollowService followService;
    @Autowired
    public FollowController(IFollowService followService) {
        this.followService = followService;
    }

    /*
    * 关注/取关
    * */
    @PostMapping("/{id}")
    public Result follow(@PathVariable("id") Long id) {
        followService.followOrCancel(id);
        return Result.ok();
    }

    /*
    * 查询id所在用户是否被当前用户关注
    * */
    @GetMapping("/or/not/{id}")
    public Result isFollowed(@PathVariable("id") Long id){
        Boolean isFollowed = followService.isFollowed(id);
        return Result.ok(isFollowed);
    }

    /*
    * 查询共同关注列表
    * */
    @GetMapping("/common/{id}")
    public Result commonFollow(@PathVariable("id") Long id){
        List<UserDTO> commonFollowedUser = followService.commonFollow(id);
        return Result.ok(commonFollowedUser);
    }
}
