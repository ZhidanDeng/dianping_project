package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserInfoService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author wandaren
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Autowired
    StringRedisTemplate stringRedisTemplate;
    @Autowired
    IUserService userService;

    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        // 获取登录用户
        final Long userId = UserHolder.getUser().getId();
        String key = RedisConstants.FOLLOWS_KEY + userId;
        // 判断是关注还是取关
        if (isFollow) {
            // 关注，新增
            final Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followUserId);
            final boolean save = save(follow);
            if (save) {
                //把关注用户放入redis的set集合
                stringRedisTemplate.opsForSet().add(key, followUserId.toString());
            }
        } else {
            // 取关，删除
            final boolean remove = remove(new QueryWrapper<Follow>().eq("user_id", userId).eq("follow_user_id", followUserId));
            if (remove) {
                // 把关注用户ID移除set集合
                stringRedisTemplate.opsForSet().remove(key, followUserId.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result isFollow(Long followUserId) {
        // 获取登录用户
        final Long userId = UserHolder.getUser().getId();
        // 查询是否关注
        final Long count = query().eq("user_id", userId).eq("follow_user_id", followUserId).count();
        return Result.ok(count > 0);
    }

    @Override
    public Result followCommons(Long id) {
        // 获取登录用户
        final Long userId = UserHolder.getUser().getId();
        // 求交集
        String key1 = RedisConstants.FOLLOWS_KEY + userId;
        String key2 = RedisConstants.FOLLOWS_KEY + id;
        final Set<String> set = stringRedisTemplate.opsForSet().intersect(key1, key2);
        if (ObjectUtils.isEmpty(set)) {
            return Result.ok(Collections.emptyList());
        }
        // 解析用户id
        final List<Long> ids = set.stream().map(Long::valueOf).collect(Collectors.toList());
        // 查询用户
        final List<UserDTO> users = userService.listByIds(ids)
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(users);
    }
}
