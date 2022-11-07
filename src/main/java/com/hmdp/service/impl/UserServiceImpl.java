package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.PhoneUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.json.JSONUtil;
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
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import javax.servlet.http.HttpSession;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author wandaren
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Override
    public Result sendCode(String phone, HttpSession session) {
        // 校验手机号
        if (!PhoneUtil.isMobile(phone)) {
            return Result.fail("手机号格式错误！");
        }
        // 模拟生成验证码
        final String code = RandomUtil.randomNumbers(6);
        // 记录验证码
        session.setAttribute("code", code);
        // 模拟发送验证码
        log.debug("发送短信验证码成功，验证码：{}", code);

        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        final String phone = loginForm.getPhone();
        final String code = (String) session.getAttribute("code");
        // 校验手机号
        if (!PhoneUtil.isMobile(phone)) {
           return Result.fail("手机号格式错误！");
        }

        // 校验验证码
        if (ObjectUtils.isEmpty(code) || !RegexUtils.isCodeInvalid(loginForm.getCode())
                || !loginForm.getCode().equals(code)) {
            return Result.fail("验证码错误！");
        }

        // 判断用户是否存在
        User user = query().eq("phone", phone).one();

        if (user == null) {
            user = createUserWithPhone(phone);
        }
        final UserDTO userDTO = new UserDTO();
        BeanUtils.copyProperties(user, userDTO);
        System.out.println(JSONUtil.toJsonStr(userDTO));
        // 保存用户信息
        session.setAttribute("user", userDTO);
        return Result.ok();
    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        save(user);
        return user;
    }
}
