package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 关注或取消关注
     * @param followUserId 关注的id
     * @param isFollow 是否关注
     * @return 响应结果
     */
    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        Long userId = UserHolder.getUser().getId();
        // 判断是关注还是取关
        if (isFollow) {
            // 关注,新增数据
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followUserId);
            // 保存到数据库
            this.save(follow);
        }else {
            // 取关, 删除SQL DELETE FROM tb_follow WHERE user_id = ? and follow_user_id = ?
            this.lambdaUpdate()
                    .eq(Follow::getFollowUserId, followUserId)
                    .eq(Follow::getUserId, userId)
                    .remove();
        }

        return Result.ok();
    }

    /**
     * 查询是否关注
     * @param followUserId 关注的id
     * @return 响应结果
     */
    @Override
    public Result isFollow(Long followUserId) {
        Long userId = UserHolder.getUser().getId();
        // 查询是否已关注
        Long count = this.lambdaQuery()
                .eq(Follow::getFollowUserId, followUserId)
                .eq(Follow::getUserId, userId)
                .count();
        // 如果结果>0,则已关注
        int res = count.intValue();

        return Result.ok(res > 0);  // true || false
    }
}
