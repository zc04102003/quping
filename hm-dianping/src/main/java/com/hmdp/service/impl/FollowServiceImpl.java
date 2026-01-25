package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
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
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private IUserService userService;
    
    /**
     * 关注或取消关注
     * @param followUserId 关注的id
     * @param isFollow 是否关注
     * @return 响应结果
     */
    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        Long userId = UserHolder.getUser().getId();
        String key = "follow:" + userId;    // Redis的Set集合Key
        // 判断是关注还是取关
        if (isFollow) {
            // 关注,新增数据
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followUserId);
            // 保存到数据库
            boolean isSuccess = this.save(follow);
            if (isSuccess) {
                // 把当前用户关注的用户id存到Redis中,基于Set集合实现 sadd key followUserId
                stringRedisTemplate.opsForSet().add(key, followUserId.toString());
            }

        }else {
            // 取关, 删除SQL DELETE FROM tb_follow WHERE user_id = ? and follow_user_id = ?
            boolean isSuccess = this.lambdaUpdate()
                    .eq(Follow::getFollowUserId, followUserId)
                    .eq(Follow::getUserId, userId)
                    .remove();
            if (isSuccess) {
                // 从Redis中删除
                stringRedisTemplate.opsForSet().remove(key, followUserId.toString());
            }
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

    /**
     * 共同关注
     * @param id 用户id
     * @return 响应结果
     */
    @Override
    public Result commonFollows(Long id) {
        // 获取当前用户id
        Long userId = UserHolder.getUser().getId();
        String key1 = "follow:" + userId;   // 当前用户的Key
        String key2 = "follow:" + id;       // 浏览用户的Key
        // 求交集, 返回共同的id-Set集合
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key1, key2);
        // 判断结果是否为空
        if (intersect == null || intersect.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        // 转换
        List<Long> ids = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
        // 根据ID批量查询
//        List<UserDTO> userDTOList = userService.listByIds(ids)
//                .stream().map(user -> BeanUtil.copyProperties(user, UserDTO.class))
//                .collect(Collectors.toList());
        List<User> userList = userService.listByIds(ids);
        // 转换成userDto集合
        List<UserDTO> userDTOList = BeanUtil.copyToList(userList, UserDTO.class);

        return Result.ok(userDTOList);
    }
}
