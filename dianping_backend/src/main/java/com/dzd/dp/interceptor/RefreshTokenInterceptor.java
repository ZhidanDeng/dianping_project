package com.dzd.dp.interceptor;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.StrUtil;
import com.dzd.dp.dto.UserDTO;
import com.dzd.dp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.dzd.dp.constant.Constant.LOGIN_EXPIRED;
import static com.dzd.dp.constant.Constant.LOGIN_TOKEN;

/**
 * Created with IntelliJ IDEA.
 *
 * @Author: DZD
 * @Date: 2023/03/25/21:52
 * @Description:
 */
public class RefreshTokenInterceptor implements HandlerInterceptor {

    private StringRedisTemplate template;

    public RefreshTokenInterceptor(StringRedisTemplate template){
        this.template = template;
    }
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String token = request.getHeader("authorization");
        if (StrUtil.isBlank(token)){
            return true;
        }
        String key = LOGIN_TOKEN + token;
        Map<Object, Object> userMap = template.opsForHash().entries(key);
        if (userMap.isEmpty()){
            return true;
        }
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldVal) -> fieldVal.toString()));
        UserHolder.saveUser(userDTO);//存到threadlocal
        template.expire(key,LOGIN_EXPIRED, TimeUnit.MINUTES);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserHolder.removeUser();
    }
}
