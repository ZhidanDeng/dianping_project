package com.dzd.dp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dzd.dp.dto.LoginFormDTO;
import com.dzd.dp.dto.Result;
import com.dzd.dp.dto.UserDTO;
import com.dzd.dp.entity.User;
import com.dzd.dp.mapper.UserMapper;
import com.dzd.dp.service.IUserService;
import com.dzd.dp.utils.RegexUtils;
import com.dzd.dp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.dzd.dp.constant.Constant.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author dzd
 * @since 2021-12-22
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Autowired
    private StringRedisTemplate template;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机格式错误！");
        }
        String code = RandomUtil.randomNumbers(6);
        //将code保存到redis中,key为LOGIN_CODE+手机号，value为code
        template.opsForValue().set(LOGIN_CODE+phone,code,CODE_EXPIRED, TimeUnit.MINUTES);
        // TODO 发送验证码
        log.debug("验证码：{}", code);
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //验证手机号时，新用户还得注册
        if (RegexUtils.isPhoneInvalid(loginForm.getPhone())) {
            return Result.fail("手机格式错误！");
        }
        String cacheCode = template.opsForValue().get(LOGIN_CODE+loginForm.getPhone());
        String code = loginForm.getCode();
        if (cacheCode == null || !cacheCode.equals(code)) {
            return Result.fail("验证码错误");
        }
        //
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getPhone,loginForm.getPhone());
        User user = getOne(wrapper);
        if (user==null){
            user = createUserPhone(loginForm.getPhone());
        }
        //随机生成token
        String token = UUID.randomUUID().toString(true);
        //将User对象转为hash存储
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO,new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((filedName,filedValue)->filedValue.toString()));
        template.opsForHash().putAll(LOGIN_TOKEN+token,userMap);

        template.expire(LOGIN_TOKEN+token,LOGIN_EXPIRED,TimeUnit.MINUTES);
        //将token返回给前端
        return Result.ok(token);
    }

    @Override
    public Result queryUserById(Long userId) {
        User user = getById(userId);
        if (user==null){
            return Result.ok();
        }
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        return Result.ok(userDTO);
    }

    private User createUserPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_PRE+RandomUtil.randomString(6));
        save(user);
        return user;
    }

    @Override
    public Result sign() {
        Long userId = UserHolder.getUser().getId();
        LocalDateTime now = LocalDateTime.now();
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyy/MM"));
        String key = SIGN_KEY+userId+keySuffix;
        int dayOfMonth = now.getDayOfMonth();
        template.opsForValue().setBit(key,dayOfMonth-1,true );//true表示1，false为0
        return Result.ok();
    }

    @Override
    public Result getSignCount() {
        Long userId = UserHolder.getUser().getId();
        LocalDateTime now = LocalDateTime.now();
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyy/MM"));
        String key = SIGN_KEY+userId+keySuffix;
        int dayOfMonth = now.getDayOfMonth();
        //bitfield sign:userId:suffix get uXX 0
        List<Long> result = template.opsForValue().bitField(
                key, BitFieldSubCommands.create()
                        .get(BitFieldSubCommands.BitFieldType.signed(dayOfMonth)).valueAt(0)
        );
        if (result == null || result.isEmpty()){
            return Result.ok(0);
        }
        Long num = result.get(0);
        if (num==null){
            return Result.ok(0);
        }
        int cal = 0;
        while (num!=0){
            long t = num&1;
            if (t==0){
                break;
            }else {
                cal++;
                num >>= 1;
            }
        }
        return Result.ok(cal);
    }
}
