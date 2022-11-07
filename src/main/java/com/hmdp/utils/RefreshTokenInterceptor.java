package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.UserDTO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.ObjectUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @Description token刷新拦截器
 * @Version 1.0.0
 * @Date 2022/11/7
 * @Author wandaren
 */
public class RefreshTokenInterceptor implements HandlerInterceptor {
    private StringRedisTemplate stringRedisTemplate;

    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
//        final HttpSession session = request.getSession();
//        final Object user = session.getAttribute("user");
        final String token = request.getHeader("authorization");
        if (ObjectUtils.isEmpty(token)) {
            return true;
        }
        String tokenKey = RedisConstants.LOGIN_USER_KEY + token;
        final Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(tokenKey);
        if (ObjectUtils.isEmpty(userMap)) {
            return true;
        }
//        UserHolder.saveUser((UserDTO) user);
        final UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
        UserHolder.saveUser(userDTO);
        // 刷新token过期时间
        stringRedisTemplate.expire(tokenKey, RedisConstants.LOGIN_USER_TTL, TimeUnit.SECONDS);

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserHolder.removeUser();
    }
}
