package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IBlogService extends IService<Blog> {
    /**
     * 查询博文
     * @param id 博文id
     * @return 响应结果
     */
    public Result queryBlogById(Long id);

    /**
     * 查询最热博文
     * @param current 当前页
     * @return 响应结果
     */
    public Result queryHotBlog(Integer current);

    /**
     * 点赞
     * @param id 博文id
     * @return 响应结果
     */
    public Result updateLikeBlog(Long id);

    /**
     * 查询博文点赞数排行
     * @param id 博文id
     * @return 响应结果
     */
    public Result selectBlogLikes(Long id);

    /**
     * 查询用户博文
     * @param id 用户id
     * @param current 当前页
     * @return 响应结果
     */
     public Result selectUserBlogs(Long id, Integer current);
}
