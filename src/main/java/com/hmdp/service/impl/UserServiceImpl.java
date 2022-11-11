package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.date.DatePattern;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.lang.UUID;
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
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

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

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        // 校验手机号
        if (!PhoneUtil.isMobile(phone)) {
            return Result.fail("手机号格式错误！");
        }
        // 模拟生成验证码
        final String code = RandomUtil.randomNumbers(6);
        // 记录验证码
//        session.setAttribute("code", code);
        stringRedisTemplate.opsForValue().set(RedisConstants.LOGIN_CODE_KEY + phone, code, RedisConstants.LOGIN_CODE_TTL, TimeUnit.MINUTES);
        // 模拟发送验证码
        log.debug("发送短信验证码成功，验证码：{}", code);

        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        final String phone = loginForm.getPhone();
        // 校验手机号
        if (!PhoneUtil.isMobile(phone)) {
            return Result.fail("手机号格式错误！");
        }
//        final String code = (String) session.getAttribute("code");
        final String code = stringRedisTemplate.opsForValue().get(RedisConstants.LOGIN_CODE_KEY + phone);
        System.out.println(code);
        System.out.println(RegexUtils.isCodeInvalid(loginForm.getCode()));
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
        // 保存用户信息
        final UserDTO userDTO = new UserDTO();
        BeanUtils.copyProperties(user, userDTO);
//        session.setAttribute("user", userDTO);
        final String token = UUID.randomUUID().toString(true);
        final Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create().setIgnoreNullValue(true).setFieldValueEditor((fid, fvalue) -> fvalue.toString()));
        String tokenKey = RedisConstants.LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
        stringRedisTemplate.expire(tokenKey, RedisConstants.LOGIN_USER_TTL, TimeUnit.SECONDS);

        //返回token
        return Result.ok(token);
    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        save(user);
        return user;
    }

    @Override
    public Result sign() {
        // 获取当前登录用户
        final Long userId = UserHolder.getUser().getId();
        // 获取日前
        final LocalDateTime now = LocalDateTime.now();
        // 拼接key
        final String keySuffix = now.format(DatePattern.SIMPLE_MONTH_FORMATTER);
        String key = RedisConstants.USER_SIGN_KEY + userId + ":" + keySuffix;
        // 计算bit位（获取今天是本月的第几天）
        final int dayOfMonth = now.getDayOfMonth();
        // 写入redis
        stringRedisTemplate.opsForValue().setBit(key, (dayOfMonth - 1), true);
        return Result.ok();
    }

    @Override
    public Result signCount() {
        // 获取当前登录用户
        final Long userId = UserHolder.getUser().getId();
        // 获取日前
        final LocalDateTime now = LocalDateTime.now();
        // 拼接key
        final String keySuffix = now.format(DatePattern.SIMPLE_MONTH_FORMATTER);
        String key = RedisConstants.USER_SIGN_KEY + userId + ":" + keySuffix;
        // 计算bit位（获取今天是本月的第几天）
        final int dayOfMonth = now.getDayOfMonth();
        // 获取本月截止今天为止的所有签到记录，返回的是一个十进制的数字
        final List<Long> result = stringRedisTemplate.opsForValue().bitField(key,
                BitFieldSubCommands.create().get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0)
        );
        // 循环遍历
        if (ObjectUtils.isEmpty(result)) {
            return Result.ok(0);
        }
        Long num = result.get(0);
        if (num == null || num == 0) {
            return Result.ok(0);
        }
        int count = 0;
        // 让这个数字与1做与运算，得到数字的最后一个bit位
        while (true) {
            // 判断这个bit位是否位0
            if ((num & 1) == 0) {
                // 如果为0，说明未签到，结束
                break;
            } else {
                // 如果为1，说明签到过，计数器+1
                count++;
            }
            // 把数字右移一位，抛弃最后一个bit位，循环直到不为1
            num >>>= 1;
        }
        return Result.ok(count);
    }
}
