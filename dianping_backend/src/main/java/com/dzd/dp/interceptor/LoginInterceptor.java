package com.dzd.dp.interceptor;

import com.dzd.dp.utils.UserHolder;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Created with IntelliJ IDEA.
 *
 * @Author: DZD
 * @Date: 2023/03/24/19:50
 * @Description: 拦截器在controller前，拦截、放行请求。controller不需要重复某些操作，比如登录操作
 */
public class LoginInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //判断是否需要拦截
        if (UserHolder.getUser() == null){
            response.setStatus(401);
            return false;
        }
        return true;
    }

}
