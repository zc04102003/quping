package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import org.springframework.beans.factory.annotation.Autowired;
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

        return Result.ok(blog);
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
            }
        });

        return Result.ok(records);
    }
}
