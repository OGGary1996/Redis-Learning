package com.hmdp.controller;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.List;

/**
 * <p>
 * 前端控制器
 * </p>
 */
@RestController
@RequestMapping("/blog")
public class BlogController {

    @Resource
    private IBlogService blogService;

    @PostMapping
    public Result saveBlog(@RequestBody Blog blog) {
        Long blogId = blogService.saveBlog(blog);
        return Result.ok(blogId);
    }

    @GetMapping("/hot")
    public Result queryHotBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        List<Blog> blogs = blogService.queryHotBlog(current);
        return Result.ok(blogs);
    }

    @GetMapping("/{id}")
    public Result queryBlogById(@PathVariable("id") Long id){
        Blog blog = blogService.queryBlogById(id);
        return Result.ok(blog);
    }

    @PutMapping("/like/{id}")
    public Result likeBlog(@PathVariable("id") Long id) {
        // 修改点赞数量
//        blogService.update()
//                .setSql("liked = liked + 1").eq("id", id).update();
        // 优化为基于Redis - set点赞
        blogService.likeBlog(id);
        return Result.ok();
    }

    @GetMapping("/likes/{id}")
    public Result queryBlogLikes(@PathVariable("id") Long id) {
        List<UserDTO> userDTOList = blogService.queryBlogLikes(id);
        return Result.ok(userDTOList);
    }

    @GetMapping("/of/me")
    public Result queryMyBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        // 根据用户查询
        Page<Blog> page = blogService.query()
                .eq("user_id", user.getId()).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        return Result.ok(records);
    }

    /*
    * 获取用户所有的探店博文，分页查询，用于在主页展示Blog和共同关注列表
    * */
    @GetMapping("/of/user")
    public Result queryBlogByUserId(@RequestParam("id") Long id, @RequestParam(value = "current",defaultValue = "1") Integer current) {
        List<Blog> records = blogService.queryBlogByUserId(id,current);
        return Result.ok(records);
    }

    /*
    * 在个人页面中查看所有关注的用户的博文
    * */
    @GetMapping("/of/follow")
    public Result queryBlogOfFollow(
            @RequestParam(value ="lastId", defaultValue= "" + Long.MAX_VALUE) Long max,
            @RequestParam(value = "offset", defaultValue= "0") Integer offset) {
        ScrollResult scrollResult = blogService.queryBlogOfFollow(max, offset);
        return Result.ok(scrollResult);
    }

}
