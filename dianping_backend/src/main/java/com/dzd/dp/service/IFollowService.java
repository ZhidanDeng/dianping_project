package com.dzd.dp.service;

import com.dzd.dp.dto.Result;
import com.dzd.dp.entity.Follow;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IFollowService extends IService<Follow> {

    Result follow(Long followUserId, Boolean isFollow);

    Result isFollowed(Long followUserId);

    Result getCommonFollows(Long id);
}
