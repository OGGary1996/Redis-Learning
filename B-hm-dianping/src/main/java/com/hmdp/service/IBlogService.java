package com.hmdp.service;

import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.entity.User;

import java.util.List;

/**
 * <p>
 *  服务类
 * </p>
 */
public interface IBlogService extends IService<Blog> {

    /*
    * 保存探店博文
    * 优化：
    *  1. 保存Blog到数据库之后，推送给用户的所有粉丝
    *  2. 利用Redis的ZSet数据结构，存储每个用户的收件箱，key格式：feed:userId，value：blogId，score：时间戳
    *  3. 这样用户在首页查看好友动态时，就可以直接从Redis中获取，而不需要每次都查询数据库
    * */
    Long saveBlog(Blog blog);

    List<Blog> queryHotBlog(Integer current);

    Blog queryBlogById(Long id);

    /*
    * 基于Redis Set数据结构实现博客点赞功能
    * 流程：
    *  1. 获取用户id
    *  2. 判断当前用户是否已经点赞，本质上是查询Redis的Set集合是否存在该用户id
    *   2.1 如果存在，说明已经点过赞，则取消点赞，数据库点赞数-1，Redis的Set集合移除该用户id
    *   2.2 如果不存在，说明没有点过赞，则点赞，数据库点赞数+1，Redis的Set集合添加该用户id
    *
    * 优化：
    *  1. Set集合是无序的，无法统计点赞的先后顺序（时间）
    *  2. 无法实现用户点赞顺序的排行榜功能
    *  3. 将Set优化为ZSet，其中的score分数表示点赞的先后顺序（时间戳）
    *  4. 这样就可以实现用户点赞顺序的排行榜功能
    * */
    void likeBlog(Long id);

    /*
    * 基于Redis ZSet数据结构实现查询博客点赞用户列表功能
    * 流程：
    *  1. 利用ZSet的倒序查询功能，查询top5的用户id
    *  2. 根据用户id查询用户信息
    *  3. 返回用户信息列表
    * */
    List<UserDTO> queryBlogLikes(Long id);

    /*
    * 获取用户所有的探店博文，分页查询，用于在主页展示Blog和共同关注列表
    * */
    List<Blog> queryBlogByUserId(Long id, Integer current);

    /*
    * 在个人页面中查看关注用户的探店博文
    * */
    ScrollResult queryBlogOfFollow(Long max, Integer offset);
}
