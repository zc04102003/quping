package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
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
        Long userId = UserHolder.getUser().getId();
        //2.判断当前登录用户是否已经点赞
        Boolean isMember = stringRedisTemplate.opsForSet().isMember(
                RedisConstants.BLOG_LIKED_KEY + blog.getId(), userId.toString());
        // 判断是否点赞
        if (BooleanUtil.isTrue(isMember)) {
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
        Boolean isMember = stringRedisTemplate.opsForSet().isMember(
                RedisConstants.BLOG_LIKED_KEY + id, userId.toString());

        if (BooleanUtil.isFalse(isMember)) {    //3.如果未点赞，可以点赞
            //3.1.数据库点赞数+1
            boolean isSuccess = lambdaUpdate()
                    .setSql("liked = liked + 1")
                    .eq(Blog::getId, id)
                    .update();
            //3.2.保存用户到Redis的set集合
            if (isSuccess){
                stringRedisTemplate.opsForSet().add(
                        RedisConstants.BLOG_LIKED_KEY + id, userId.toString());
            }
        }else { //4.如果已点赞，取消点赞
            //4.1数据库点赞数-1
            boolean isSuccess = lambdaUpdate()
                    .setSql("liked = liked - 1")
                    .eq(Blog::getId, id)
                    .update();
            if (isSuccess) {
                //4.2.把用户从Redis的set集合移除
                stringRedisTemplate.opsForSet().remove(
                        RedisConstants.BLOG_LIKED_KEY + id, userId.toString());
            }
        }
        return Result.ok();
    }
}
