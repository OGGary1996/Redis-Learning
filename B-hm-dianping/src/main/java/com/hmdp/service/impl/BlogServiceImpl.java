package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {
    private final IUserService userService;
    private final StringRedisTemplate stringRedisTemplate;
    @Autowired
    public BlogServiceImpl(IUserService userService, StringRedisTemplate stringRedisTemplate) {
        this.userService = userService;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /*
    * 保存探店博文
    * 优化：
    *  1. 保存Blog到数据库之后，推送给用户的所有粉丝
    *  2. 利用Redis的ZSet数据结构，存储每个用户的收件箱，key格式：feed:userId，value：blogId，score：时间戳
    *  3. 这样用户在首页查看好友动态时，就可以直接从Redis中获取，而不需要每次都查询数据库
    * */
    @Override
    public Long saveBlog(Blog blog) {
        // 获取当前的用户
        Long userId = UserHolder.getUser().getId();
        blog.setUserId(userId);
        // 保存探店博文
        boolean isSuccess = save(blog);
        if (!isSuccess) {
            throw new RuntimeException("保存笔记失败");
        }
        // 如果保存成功，推送给所有粉丝
          // 1. 首先获取到当前用户的所有的粉丝
        Set<String> members = stringRedisTemplate.opsForSet().members(RedisConstants.USER_FOLLOW_KEY + userId);
          // 2.判断是否有粉丝，如果没有粉丝，则不需要推送的步骤
        if (members == null || members.isEmpty()) {
            // 直接返回blogId，方法结束
            return blog.getId();
        }
          // 3. 转换为Long类型的用户id列表
        List<Long> fansIds = members.stream().map(Long::valueOf).collect(Collectors.toList());

        // 4. 遍历粉丝列表，推送到每一个粉丝的feed流中(收件箱)
        fansIds.forEach(fansId -> {
            stringRedisTemplate.opsForZSet().add(
                    RedisConstants.FEED_KEY + fansId,
                    blog.getId().toString(),
                    System.currentTimeMillis()
            );
        });

        // 5.推送完成，返回博客id
        return blog.getId();
    }

    @Override
    public List<Blog> queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        /*
        * 补充：查询完成之后，需要单独为非数据库字段复制
        * */
        records.forEach(blog ->{
            // 1. 补充作者信息
            Long userId = blog.getUserId();
            User user = userService.getById(userId);
            blog.setName(user.getNickName());
            blog.setIcon(user.getIcon());
            // 2. 补充isLiked字段，判断当前登录用户是否点赞
            String key = RedisConstants.BLOG_LIKED_KEY + blog.getId();
            boolean isLiked = stringRedisTemplate.opsForZSet().score(key, userId.toString()) != null;
            if (isLiked) {
                blog.setIsLike(true);
            }else {
                blog.setIsLike(false);
            }
        });

        return records;
    }

    @Override
    public Blog queryBlogById(Long id) {
        // 查询博客
        Blog blog = getById(id);
        if (blog == null) {
            throw new RuntimeException("笔记不存在");
        }
        /*
        * 补充：查询完成之后，需要单独为非数据库字段复制
        * */
        // 1. 补充作者信息
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
        // 2. 补充isLiked字段，判断当前登录用户是否点赞
        String key = RedisConstants.BLOG_LIKED_KEY + id;
        boolean isLiked = stringRedisTemplate.opsForZSet().score(key, userId.toString()) != null;
        if (isLiked) {
            blog.setIsLike(true);
        }else {
            blog.setIsLike(false);
        }
        return blog;
    }

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
    @Override
    public void likeBlog(Long id) {
        // 获取用户并且判断是否在set中
        // 注意，在首页中，如果用户为登录，此时直接获取的UserHolder.getUser()会报空指针异常
        // 所以需要判断用户是否登录，如果没登录，直接不查询
        UserDTO currentUser = UserHolder.getUser();
        if (currentUser == null) {
            // 用户未登录，直接返回
            return;
        }
        Long userId = currentUser.getId();
        // 判断是否点赞过
          // 1. 构建Redis的key
        String key = RedisConstants.BLOG_LIKED_KEY + id;
          // 2. 判断是否存在于Zset集合中，本质上，如果score为null，则说明不在ZSet中
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        // 判断是否点赞
        if (score != null) {
            // 如果存在，取消点赞
            boolean isSuccess = update().setSql("liked = liked -1").eq("id", id).update();
            if (isSuccess) {
                // 并且需要把用户id从Redis的ZSet集合移除
                stringRedisTemplate.opsForZSet().remove(RedisConstants.BLOG_LIKED_KEY + id, userId.toString());
                // 同时维护BLOG_SCORE_KEY的有序集合，减少分数
                stringRedisTemplate.opsForZSet().incrementScore(RedisConstants.BLOG_SCORE_KEY, id.toString(), -1);
            }
        }else {
            // 如果不在，点赞
            boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
            if (isSuccess) {
                // 并且需要把用户id存入Redis的ZSet集合,并且需要添加当前时间作为score
                stringRedisTemplate.opsForZSet().add(RedisConstants.BLOG_LIKED_KEY + id, userId.toString(), System.currentTimeMillis());
                // 同时维护BLOG_SCORE_KEY的有序集合，增加分数
                stringRedisTemplate.opsForZSet().incrementScore(RedisConstants.BLOG_SCORE_KEY, id.toString(), 1);
            }
        }
    }

    /*
    * 基于Redis ZSet数据结构实现查询博客点赞用户列表功能
    * 流程：
    *  1. 利用ZSet的倒序查询功能，查询top5的用户id
    *  2. 根据用户id查询用户信息
    *  3. 返回用户信息列表
    * */
    @Override
    public List<UserDTO> queryBlogLikes(Long id) {
        // 1. 获取用户id
        Set<String> userIdStrSet = stringRedisTemplate.opsForZSet().reverseRange(RedisConstants.BLOG_LIKED_KEY + id, 0, 4);
        if (userIdStrSet == null || userIdStrSet.isEmpty()) {
            // 为了防止空指针异常，返回null，表示没有用户点赞
            return null;
        }
        // 2. 转换为Long类型的用户id列表，并且保持了顺序
        List<Long> userIdList = userIdStrSet.stream().map(userIdStr -> Long.valueOf(userIdStr)).toList();
        // 3. 根据用户id列表查询用户,采用MyBatis-Plus的listByIds方法
        List<User> userList = userService.listByIds(userIdList);
        // 4. 因为 listByIds 可能不保证顺序，我们手动按 userIdList 排序
          // 4.1 先将 userList 转换为 Map，key 为用户 id，value 为 User 对象；因为userIdList中的userId是有序的
        Map<Long, User> userMap = userList.stream()
                .collect(Collectors.toMap(User::getId, user -> user));
          // 4.2 然后根据 userIdList 的顺序，从userMap中获取到User对象，组装有序的 UserDTO 列表
        return userIdList.stream().map(userId -> {
            // 从userMap中获取到User对象
            User user = userMap.get(userId);
            // 转换为UserDTO
            UserDTO userDTO = new UserDTO();
            BeanUtils.copyProperties(user, userDTO);
            return userDTO;
        }).toList();
    }

    /*
    * 获取用户所有的探店博文，分页查询，用于在主页展示Blog和共同关注列表
    * 流程：
    *  1. 根据用户id分页查询博客
    *  2. 获取Page对象
    *  3. 获取Page对象中的records
    * */
    @Override
    public List<Blog> queryBlogByUserId(Long id, Integer current) {
        Page<Blog> pageResults = query().eq("user_id", id).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        return pageResults.getRecords();
    }

    /*
    * 在个人页面中查看所有已关注的用户的博文
    * 流程：
    *  1. 根据当前用户id，
    *  2. 使用当前的参数 max 和 offset进行查询，获取到消息列表，feed:userId
    *  3. 解析获取到的消息，需要获取到：blogId，时间戳，提供给下次查询的max和offset
    *  4. 根据获取到的blogId列表，查询数据库，获取到Blog列表
    *  5. 返回ScrollResult对象
    * */
    @Override
    public ScrollResult queryBlogOfFollow(Long max, Integer offset) {
        // 1. 获取当前用户id
        Long userId = UserHolder.getUser().getId();
        // 2. 滚动查询
          // 获取到的typedTuples中包含了元素和分数（时间戳）
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(RedisConstants.FEED_KEY + userId, 0, max, offset, 2);
        if (typedTuples == null || typedTuples.isEmpty()) { // 如果没有数据，返回空结果
            return ScrollResult.builder()
                    .list(List.of())
                    .minTime(0L)
                    .offset(0)
                    .build();
        }
        // 3. 解析数据，获取blogId列表，和下次分页的参数
        List<Long> blogIds = new ArrayList<>(typedTuples.size());
        long minTime = 0; // 记录本次查询的最小时间戳，用于下次查询时的max参数，默认是0，因为至少会被覆盖
        int nextOffset = 1; // 记录本次查询时最小时间戳的个数，用于下次查询的offset参数，默认是1，因为至少需要跳过1个
        for (ZSetOperations.TypedTuple<String> tuple : typedTuples) {
            // 获取blogId
            String blogIdStr = tuple.getValue();
            blogIds.add(Long.valueOf(blogIdStr));
            // 获取时间戳
            long time = tuple.getScore().longValue();
            // 判断获取的时间戳是否等于minTime，如果等于，则需要增加nextOffset
            if (time == minTime) { // 注意，第一次获取到时，肯定是不等于minTime的，所以第一次会走else分支
                nextOffset++;
            } else { // 如果不等于，则说明是一个新的更小的时间戳，更新minTime，并且重置nextOffset为1
                minTime = time;
                nextOffset = 1;
            }
        }
        // 4. 根据 blogId 列表查询数据库，使用MyBatis-Plus的listByIds方法
        List<Blog> blogs = listByIds(blogIds);
        // 注意：listByIds方法返回的blogs列表顺序是不确定的，需要进行排序
        // 创建一个Map，其中的key是来自于blogs中的blogId，value是来自于blogs列表的Blog对象
        Map<Long, Blog> blogsMap = blogs.stream().collect(Collectors.toMap(Blog::getId, Blog -> Blog));
        // 然后根据blogIds的顺序，从blogMap中获取到Blog对象
        List<Blog> blogList = blogIds.stream().map(blogId -> {
            // 从blogsMap中获取到Blog对象
            Blog blog = blogsMap.get(blogId);
            // 1. 补充作者信息
            Long authorId = blog.getUserId();
            User user = userService.getById(userId);
            blog.setName(user.getNickName());
            blog.setIcon(user.getIcon());
            // 2. 补充isLiked字段，判断当前登录用户是否点赞
            String key = RedisConstants.BLOG_LIKED_KEY + blog.getId();
            boolean isLiked = stringRedisTemplate.opsForZSet().score(key, userId.toString()) != null;
            if (isLiked) {
                blog.setIsLike(true);
            }else {
                blog.setIsLike(false);
            }
            return blog;
        }).toList();

        // 5. 返回
        return ScrollResult.builder()
                .list(blogList)
                .minTime(minTime)
                .offset(nextOffset)
                .build();
    }
}
