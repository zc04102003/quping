package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.*;
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
    @Autowired
    private IUserService userService;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private IFollowService followService;

    /**
     * 查询博文
     * @param id 博文id
     * @return 响应结果
     */
    @Override
    public Result queryBlogById(Long id) {
        // 根据ID查询博文信息
        Blog blog = lambdaQuery().eq(Blog::getId, id).one();
        if (blog == null) {
            return Result.fail("没有该博客!");
        }
        // 查询blog有关的用户
        User user = userService.getById(blog.getUserId());
        // 给blog赋值
        blog.setName(user.getNickName());   // 昵称
        blog.setIcon(user.getIcon());   // 头像
        // 查询blog是否已经被当前用户点赞了
        isBlogLiked(blog);

        return Result.ok(blog);
    }

    /**
     * 判断当前用户是否点赞了该博文
     * @param blog 博文
     */
    private void isBlogLiked(Blog blog) {
        //1.获取登录用户
        UserDTO userDTO = UserHolder.getUser();
        if (userDTO == null){
            // 用户未登录,无需查询是否点赞
            return;
        }
        Long userId = userDTO.getId();

        //2.判断当前登录用户是否已经点赞
        Double score = stringRedisTemplate.opsForZSet().score(
                RedisConstants.BLOG_LIKED_KEY+ blog.getId(),
                userId.toString()
        );
        // 判断是否点赞
        if (score != null) {
            blog.setIsLike(true);
        }
    }

    /**
     * 查询最热博文
     * @param current 当前页
     * @return 响应结果
     */
    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = this.query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        if (records == null || records.isEmpty()) {
            return Result.fail("没有最热博文");
        }

        // 搜集所有用户的ID进行批量查询
        List<Long> userIds = records.stream()
                .map(Blog::getUserId)
                .distinct()
                .collect(Collectors.toList());
        List<User> users = userService.listByIds(userIds);  // 批量查询用户信息
        // 封装用户信息
        Map<Long, User> collect = users.stream()
                .collect(Collectors.toMap(User::getId, user -> user));

        records.forEach(blog ->{
            // 获取用户
            User user = collect.get(blog.getUserId());
            if (user != null) {
                blog.setName(user.getNickName());
                blog.setIcon(user.getIcon());
                // 判断是否点赞
                this.isBlogLiked(blog);
            }
        });

        return Result.ok(records);
    }

    /**
     * 点赞
     * @param id 博文id
     * @return 响应结果
     */
    @Override
    public Result updateLikeBlog(Long id) {
        //1.获取登录用户
        Long userId = UserHolder.getUser().getId();
        //2.判断当前登录用户是否已经点赞
        Double score = stringRedisTemplate.opsForZSet().score(  // 获取当前用户点赞的分数:没有则返回null
                RedisConstants.BLOG_LIKED_KEY + id,
                userId.toString()
        );

        if (score == null) {    //3.如果未点赞，可以点赞
            //3.1.数据库点赞数+1
            boolean isSuccess = lambdaUpdate()
                    .setSql("liked = liked + 1")
                    .eq(Blog::getId, id)
                    .update();
            //3.2.保存用户到Redis的set集合 zadd key value score
            if (isSuccess){
                stringRedisTemplate.opsForZSet().add(
                        RedisConstants.BLOG_LIKED_KEY + id,
                        userId.toString(),  // 用户ID
                        System.currentTimeMillis()  // 时间戳
                );
            }
        }else { //4.如果已点赞，取消点赞
            //4.1数据库点赞数-1
            boolean isSuccess = lambdaUpdate()
                    .setSql("liked = liked - 1")
                    .eq(Blog::getId, id)
                    .update();
            if (isSuccess) {
                //4.2.把用户从Redis的set集合移除
                stringRedisTemplate.opsForZSet().remove(
                        RedisConstants.BLOG_LIKED_KEY + id,
                        userId.toString()
                );
            }
        }
        return Result.ok();
    }

    /**
     * 查询点赞用户排行
     * @param id 博文id
     * @return 响应结果
     */
    @Override
    public Result selectBlogLikes(Long id) {
        // 查询点赞top5的用户 zrange key 0 4
        // 获取点赞用户
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(
                RedisConstants.BLOG_LIKED_KEY + id, 0, 4
        );
        // 空值处理
        if (top5 == null || top5.isEmpty()) {
            // 返回空集合
            return Result.ok(Collections.emptyList());
        }
        // 获取用户ID
        List<Long> ids = top5.stream().map(Long::valueOf).collect(Collectors.toList());
        String idsJoin = StrUtil.join(",", ids);

        // 根据ID批量查询用户
//        List<UserDTO> userDTOList = userService.lambdaQuery()
//                        .in(User::getId, ids)
//                        .last("ORDER BY FIELD(id," + idsJoin + ")")
//                        .list().stream()
//                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
//                .collect(Collectors.toList());
        // hutu包直接拷贝转换
        // 修改Bug:点赞排序问题:先点赞的排在了后面,要该SQL语句 WHERE id IN (5 , 1)ORDER BY FIELD(id,5,1)
        List<UserDTO> userDTOList = BeanUtil.copyToList(
                userService.lambdaQuery()
                        .in(User::getId, ids)
                        .last("ORDER BY FIELD(id," + idsJoin + ")")
                        .list(),
                UserDTO.class);

        return Result.ok(userDTOList);
    }

    /**
     * 查询用户博文
     * @param id 用户id
     * @param current 当前页
     * @return 响应结果
     */
    @Override
    public Result selectUserBlogs(Long id, Integer current) {
        // 根据用户id查询对应的博文
        Page<Blog> page = lambdaQuery()
                .eq(Blog::getUserId, id)
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> blogList = page.getRecords();

        return Result.ok(blogList);
    }

    /**
     * 添加博文
     * @param blog 博文信息
     * @return 响应结果
     */
    @Override
    public Result addBlog(Blog blog) {
        // 获取登录用户ID
        Long userId = UserHolder.getUser().getId();
        blog.setUserId(userId);
        // 新增探店笔记
        boolean isSuccess = this.save(blog);
        if (!isSuccess) {
            return Result.fail("新增笔记失败");
        }
        // 查询笔记作者的粉丝 SELECT * FORM tb_follow WHERE follow_id = ?
        List<Follow> followList = followService.lambdaQuery()
                .eq(Follow::getFollowUserId, userId)
                .list();
        // 推送笔记ID给所有粉丝
        if (followList != null && !followList.isEmpty()) {
            followList.forEach(follow -> {
                // 获取粉丝ID
                Long fansId = follow.getUserId();
                // 推送到粉丝的邮箱中(Redis的zSet中)
                stringRedisTemplate.opsForZSet().add(
                        RedisConstants.FEED_KEY + fansId,   // 订阅粉丝ID组成的key
                        blog.getId().toString(),    // 笔记ID
                        System.currentTimeMillis()  // 时间戳
                );
            });
        }

        return Result.ok(blog.getId());
    }

    /**
     * 查询用户关注博文
     * @param max 最大时间戳
     * @param offset 偏移量
     * @return 响应结果
     */
    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        // 获取当前用户ID
        Long userId = UserHolder.getUser().getId();
        // 查询收件箱(查询Redis中的zset) ZREVRANGEBYSCORE key Max Min LIMIT offset count
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet().reverseRangeByScoreWithScores(
                RedisConstants.FEED_KEY + userId, 0, max, offset, 3
        );
        // 非空判断
        if (typedTuples == null || typedTuples.isEmpty()) {
            return Result.ok();
        }
        // 解析数据：blogId、minTime（时间戳）、offset
        List<Long> ids = new ArrayList<>(typedTuples.size());
        long minTime = 0;
        int os = 1;

        for (ZSetOperations.TypedTuple<String> tuple : typedTuples) {
            // 获取ID
            String idStr = tuple.getValue();
            if (idStr != null) {
                ids.add(Long.valueOf(idStr));
            }
            // 获取分数
            long timestamp = tuple.getScore().longValue();
            if (timestamp == minTime) {
                // 获取的分数一致，则偏移量+1
                os++;
            }else{
                // 获取的分数不一致，则更新时间戳
                minTime = timestamp;
                os = 1; // 重置偏移量
            }
        }
        // 根据ID批量查询blog
        String idsJoin = StrUtil.join(",", ids);
        List<Blog> blogList = lambdaQuery()
                .in(Blog::getId, ids)
                .last("ORDER BY FIELD(id," + idsJoin + ")")
                .list();

        // 填充用户信息
        // 获取用户ID
        List<Long> userIds = blogList.stream().map(Blog::getUserId).collect(Collectors.toList());
        // 根据ID批量查询用户信息
        List<User> userList = userService.lambdaQuery().in(User::getId, userIds).list();
        // 将ID和用户对象作为键值对放到Map中
        Map<Long, User> collect = userList.stream().collect(Collectors.toMap(User::getId, user -> user));

        blogList.forEach(blog -> {
            // 查询blog有关的用户
            User user = collect.get(blog.getUserId());
            // 给blog赋值
            blog.setName(user.getNickName());   // 昵称
            blog.setIcon(user.getIcon());   // 头像
            // 查询blog是否已经被当前用户点赞了
            isBlogLiked(blog);
        });

        // 封装结果返回
        ScrollResult result = new ScrollResult();
        result.setList(blogList);
        result.setMinTime(minTime);
        result.setOffset(os);

        return Result.ok(result);
    }
}
