package com.dzd.dp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.dzd.dp.dto.Result;
import com.dzd.dp.dto.UserDTO;
import com.dzd.dp.entity.Follow;
import com.dzd.dp.mapper.FollowMapper;
import com.dzd.dp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dzd.dp.service.IUserService;
import com.dzd.dp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.dzd.dp.constant.Constant.FOLLOWS_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Autowired
    private StringRedisTemplate template;
    @Autowired
    private IUserService userService;

    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        Long userId = UserHolder.getUser().getId();
        String key = FOLLOWS_KEY+userId;
        if (isFollow){
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followUserId);
            boolean isSave = save(follow);
            if (isSave){
                template.opsForSet().add(key,followUserId.toString());
            }
        }else {
            boolean isSuccess = remove(new QueryWrapper<Follow>()
                    .eq("user_id", userId).eq("follow_user_id", followUserId)
            );
            if (isSuccess){
                template.opsForSet().remove(key,followUserId.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result isFollowed(Long followUserId) {
        Long userId = UserHolder.getUser().getId();
        Integer count = query().eq("user_id", userId).eq("follow_user_id", followUserId).count();
        return Result.ok(count>0);
    }

    @Override
    public Result getCommonFollows(Long id) {
        Long userId = UserHolder.getUser().getId();
        String key1 = FOLLOWS_KEY+userId;
        String key2 = FOLLOWS_KEY+id;

        Set<String> commonFollows = template.opsForSet().intersect(key1, key2);
        if (commonFollows!=null){
            List<Long> followIds = commonFollows.stream().map(Long::valueOf).collect(Collectors.toList());
            List<UserDTO> userDTOS = userService.listByIds(followIds)
                    .stream()
                    .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                    .collect(Collectors.toList());
            return Result.ok(userDTOS);
        }
        return Result.ok(Collections.emptyList());
    }
}
