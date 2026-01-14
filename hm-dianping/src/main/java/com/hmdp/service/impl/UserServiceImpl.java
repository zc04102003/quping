package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.toolkit.BeanUtils;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;
import java.time.LocalDateTime;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    @Autowired
    private UserMapper userMapper;

    /**
     * 发送手机验证码
     * @param phone 手机号
     * @param session  session
     * @return
     */
    @Override
    public Result sendCode(String phone, HttpSession session) {
        // 1.校验手机号
        if ((RegexUtils.isPhoneInvalid(phone))) {
            // 2.不符合，返回错误信息
            return Result.fail("手机号格式错误!");
        }
        // 3.符合，生成验证码
        // TODO 可以用第三方短信平台(阿里云)
        String code = RandomUtil.randomNumbers(6);  // 生成6位数字验证码
        // 4.保存验证码到session
        session.setAttribute("code", code);
        // 5.发送验证码
        log.info("验证码请求成功, 验证码为: {}", code);

        // 返回ok
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        // 效验手机号
        if ((RegexUtils.isPhoneInvalid(loginForm.getPhone()))) {
            // 2.不符合，返回错误信息
            return Result.fail("手机号格式错误!");
        }
        // 效验验证码
        // TODO 这里有个坑,需要验证当前登录手机号是否为之前申请验证码的手机号
        Object cacheCode = session.getAttribute("code");
        String code = loginForm.getCode();
        if (cacheCode == null || !code.equals(cacheCode.toString()))
            return Result.fail("验证码错误!");

        // 查询数据库中是否有该条记录
//        LambdaQueryChainWrapper<User> eq = lambdaQuery().eq(User::getPhone, loginForm.getPhone());
//        User user = userMapper.selectOne(eq);
        User user = query().eq("phone", loginForm.getPhone()).one();
        if (user == null) {
            // 用户不存在,创建新用户
           user = createUserWithPhone(loginForm.getPhone());
        }
        // 存在,则保存到session中
        // userDto不含敏感信息
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        session.setAttribute("user", userDTO);
        return Result.ok();
    }

    /**
     * 创建新用户
     * @param phone 手机号
     * @return 新用户信息
     */
    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        // 随机生成昵称 : user_*
//        user.setNickName("user_" + RandomUtil.randomString(6));
        user.setNickName(SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomString(6));
        // TODO 可以用切面类实现新增用户创建时间和修改时间
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());

        // 保存到数据库
        save(user);

        return user;
    }
}
