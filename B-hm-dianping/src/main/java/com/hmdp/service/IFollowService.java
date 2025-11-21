package com.hmdp.service;

import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IFollowService extends IService<Follow> {

    /*
    * 关注/取关
    * */
    void followOrCancel(Long id);

    /*
    * 查询id所在用户是否被当前用户关注
    * */
    Boolean isFollowed(Long id);

    /*
    * 查询共同关注列表
    * */
    List<UserDTO> commonFollow(Long id);
}
