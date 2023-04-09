package com.dzd.dp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dzd.dp.dto.LoginFormDTO;
import com.dzd.dp.dto.Result;
import com.dzd.dp.entity.User;

import javax.servlet.http.HttpSession;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IUserService extends IService<User> {
    Result sendCode(String phone, HttpSession session);

    Result login(LoginFormDTO loginForm, HttpSession session);

    Result queryUserById(Long userId);

    Result sign();

    Result getSignCount();
}
