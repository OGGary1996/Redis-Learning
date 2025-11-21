package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * <p>
 *  服务实现类
 * </p>
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {
    private final StringRedisTemplate stringRedisTemplate;
    private final IUserService userService;
    @Autowired
    public FollowServiceImpl(StringRedisTemplate stringRedisTemplate, IUserService userService) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.userService = userService;
    }

    /*
    * 关注/取关
    * 流程：
    *  1. 根据currentUserId查询follow:{id} Set中是否存在id
    *  2. 如果存在，则说明已经关注了，执行取关操作
    *  3. 如果不存在，则说明未关注，执行关注操作
    * */
    @Override
    public void followOrCancel(Long id) {
        // 1. 获取当前用户id
        Long currentUserId = UserHolder.getUser().getId();
        // 2. 判断是否已经关注
        Boolean isMember = stringRedisTemplate.opsForSet().isMember(RedisConstants.USER_FOLLOW_KEY + currentUserId, id.toString());
        if (Boolean.TRUE.equals(isMember)) {
            // 2.1 如果为true，则说明Set中存在id，已经关注了，执行取关操作
            // 删除数据库中的关注记录,通过MyBatis-Plus提供的remove方法
            LambdaQueryWrapper<Follow> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(Follow::getUserId,currentUserId).eq(Follow::getFollowUserId,id);
            boolean isRemoved = remove(queryWrapper);
            if (isRemoved) {
                // 删除Redis Set中的id
                stringRedisTemplate.opsForSet().remove(RedisConstants.USER_FOLLOW_KEY + currentUserId, id.toString());
            }
        }else {
            // 2.2 如果为false，则说明Set中不存在id，未关注，执行关注操作
            // 创建关注记录到数据库,通过MyBatis-Plus提供的save方法
            Follow follow = new Follow();
            follow.setUserId(currentUserId);
            follow.setFollowUserId(id);
            boolean isSaved = save(follow);
            if (isSaved) {
                // 将id添加到Redis Set中
                stringRedisTemplate.opsForSet().add(RedisConstants.USER_FOLLOW_KEY + currentUserId, id.toString());
            }
        }
    }

    /*
    * 查询id所在用户是否被当前用户关注
    * 流程：
    *  1. 根据currentUserId查询follow:{id} Set中是否存在id
    *  2. 如果存在，则说明已经关注了，设置isFollowed 为true
    *  3. 如果不存在，则说明未关注，设置isFollowed 为false
    * */
    @Override
    public Boolean isFollowed(Long id) {
        // 1. 获取当前用户id
        Long currentUserId = UserHolder.getUser().getId();
        // 2. 判断是否已经关注
        return stringRedisTemplate.opsForSet().isMember(RedisConstants.USER_FOLLOW_KEY + currentUserId, id.toString());
    }

    /*
    * 查询共同关注列表
    * 流程：
    *  1. 获取当前用户 currentId
    *  2. 分别获取follow:currentId 和 follow:id 两个Set，求两个Set的交集
    *  3. 根据交集中的用户id列表，逐个查询用户信息
    *  4. 直接调用userSerivice中的queryByUserId方法，直接获取到UserDTO对象
    *  5. 注意：此时获取到的UserDTO对象中已经包含了isFollowed字段
    * */
    @Override
    public List<UserDTO> commonFollow(Long id) {
        // 1. 获取当前用户 currentId
        Long currentUserId = UserHolder.getUser().getId();

        // 2. 分别获取follow:currentId 和 follow:id 两个Set，求两个Set的交集
        String key1 = RedisConstants.USER_FOLLOW_KEY + currentUserId;
        String key2 = RedisConstants.USER_FOLLOW_KEY + id;
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key1, key2);
        if (intersect == null || intersect.isEmpty()) {
            // 如果交集为空，说明没有共同关注，直接返回空列表
            return List.of();
        }
        // 如果不为空，转换为Long类型的id列表
        List<Long> ids = intersect.stream().map(Long::valueOf).toList();

        // 3. 根据交集中的用户id列表，逐个查询用户信息
        return ids.stream().map(userId -> userService.queryByUserId(userId)).toList();
    }
}
