package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.service.IFollowService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/follow")
public class FollowController {
    @Autowired
    private IFollowService followService;

    /**
     * 关注
     * @param followUserId 关注的id
     * @param isFollow 是否关注
     * @return 响应结果
     */
    @PutMapping("/{id}/{isFollow}")
    public Result follow(@PathVariable("id") Long followUserId, @PathVariable Boolean isFollow){
        return followService.follow(followUserId, isFollow);
    }

    /**
     * 查询是否关注
     * @param followUserId 关注的id
     * @return 响应结果
     */
    @GetMapping("/or/not/{id}")
    public Result isFollow(@PathVariable("id") Long followUserId){
        return followService.isFollow(followUserId);
    }

    /**
     * 查询共同关注
     * @param id 用户id
     * @return 响应结果
     */
    @GetMapping("/common/{id}")
    public Result commonFollows(@PathVariable Long id){
        return followService.commonFollows(id);
    }
}
