package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import javax.annotation.Resource;
import java.util.*;
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
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {
    @Resource
    private IUserService userService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private IFollowService followService;

    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = getBlogPage(current);
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog -> {
            this.queryBlogUser(blog);
            this.isBlogLiked(blog);
        });
        return Result.ok(records);
    }

    public Page<Blog> getBlogPage(Integer current) {
        return query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
    }

    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }

    @Override
    public Result queryBlogById(Long id) {
        // 1、查询blog
        final Blog blog = getById(id);
        if (blog == null) {
            return Result.fail("笔记不存在！");
        }
        // 2、查询blog有关用户
        queryBlogUser(blog);
        //3、查询blog是否被点赞
        isBlogLiked(blog);
        return Result.ok(blog);
    }

    //    private void isBlogLiked(Blog blog) {
//        //1、获取登陆用户
//        final Long userId = UserHolder.getUser().getId();
//        //2、判断当前登录用户是否已经点赞
//        String key = RedisConstants.BLOG_LIKED_KEY + blog.getId();
//        final Boolean member = stringRedisTemplate.opsForSet().isMember(key, userId.toString());
//        blog.setIsLike(BooleanUtil.isTrue(member));
//    }
    private void isBlogLiked(Blog blog) {
        //1、获取登陆用户
        final UserDTO user = UserHolder.getUser();
        if (user == null) {
            // 用户未登录，直接返回
            return;
        }
        final Long userId = user.getId();
        //2、判断当前登录用户是否已经点赞
        String key = RedisConstants.BLOG_LIKED_KEY + blog.getId();
        final Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        blog.setIsLike(score != null);
    }

//    @Override
//    public Result likeBlog(Long id) {
//        //1、获取登陆用户
//        final Long userId = UserHolder.getUser().getId();
//        //2、判断当前登录用户是否已经点赞
//        String key = RedisConstants.BLOG_LIKED_KEY + id;
//        final Boolean member = stringRedisTemplate.opsForSet().isMember(key, userId.toString());
//        if (BooleanUtil.isFalse(member)) {
//            //3、如果未点赞，可以点赞
//            //3.1、数据库点赞数+1
//            final boolean isUpdate = update().setSql("liked = liked + 1").eq("id", id).update();
//            //3.2、保存用户到redis的set集合
//            log.error("isUpdate---------" + isUpdate);
//            if (isUpdate) {
//                stringRedisTemplate.opsForSet().add(key, userId.toString());
//            }
//        } else {
//            //4、如果已点赞，取消之前点赞
//            //4.1、数据库点赞-1
//            final boolean isUpdate = update().setSql("liked = liked - 1").eq("id", id).update();
//            //4.2、把用户从redis的set集合中移除
//            if (isUpdate) {
//                stringRedisTemplate.opsForSet().remove(key, userId.toString());
//            }
//        }
//        return Result.ok();
//    }

    @Override
    public Result likeBlog(Long id) {
        //1、获取登陆用户
        final Long userId = UserHolder.getUser().getId();
        //2、判断当前登录用户是否已经点赞
        String key = RedisConstants.BLOG_LIKED_KEY + id;
        final Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        if (score == null) {
            //3、如果未点赞，可以点赞
            //3.1、数据库点赞数+1
            final boolean isUpdate = update().setSql("liked = liked + 1").eq("id", id).update();
            //3.2、保存用户到redis的zSet集合
            log.error("isUpdate---------" + isUpdate);
            if (isUpdate) {
                stringRedisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
            }
        } else {
            //4、如果已点赞，取消之前点赞
            //4.1、数据库点赞-1
            final boolean isUpdate = update().setSql("liked = liked - 1").eq("id", id).update();
            //4.2、把用户从redis的zSet集合中移除
            if (isUpdate) {
                stringRedisTemplate.opsForZSet().remove(key, userId.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result queryBlogLikes(Long id) {
        //查询top5数据
        String key = RedisConstants.BLOG_LIKED_KEY + id;
        final Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if (ObjectUtils.isEmpty(top5)) {
            return Result.ok(Collections.emptyList());
        }
        // 解析用户id
        final List<Long> ids = top5.stream().map(Long::valueOf).collect(Collectors.toList());
        String idStr = StrUtil.join(",", ids);
        final List<UserDTO> userDTOS = userService.query().in("id", ids)
                .last("order by field(id," + idStr + ")").list()
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(userDTOS);
    }

    @Override
    public Result saveBlog(Blog blog) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 保存探店博文
        final boolean save = save(blog);
        if (save) {
            Result.fail("新增笔记失败！");
        }
        // 查询笔记作者的粉丝
        final List<Follow> follows = followService.query().eq("follow_user_id", user.getId()).list();
        // 推送笔记id给所有粉丝
        final Long blogId = blog.getId();
        for (Follow follow : follows) {
            final Long userId = follow.getUserId();
            String key = RedisConstants.FEED_KEY + userId;
            stringRedisTemplate.opsForZSet().add(key, blogId.toString(), System.currentTimeMillis());
        }
        // 返回id
        return Result.ok(blogId);
    }

    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        // 获取当前用
        final Long userId = UserHolder.getUser().getId();
        // 查询收件箱
        String key = RedisConstants.FEED_KEY + userId;
        // 每页查几条
        int count = 3;
        final Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(key, 0, max, offset, count);
        if (ObjectUtils.isEmpty(typedTuples)) {
            return Result.ok();
        }
        // 解析数据
        List<Long> ids = new ArrayList<>(typedTuples.size());
        long minTime = 0;
        int os = 1;
        for (ZSetOperations.TypedTuple<String> tuple : typedTuples) {
            // 获取ID
            ids.add(Long.valueOf(Objects.requireNonNull(tuple.getValue())));
            // 获取分数（时间戳）
            long time = Objects.requireNonNull(tuple.getScore()).longValue();
            if (time == minTime) {
                os++;
            } else {
                minTime = time;
            }
        }
        // 根据ID查询blog
        String idStr = StrUtil.join(",", ids);
        final List<Blog> blogs = query().in("id", ids)
                .last("order by field(id," + idStr + ")").list();
        for (Blog blog : blogs) {
            // 查询blog有关用户
            queryBlogUser(blog);
            // 查询blog是否被点赞
            isBlogLiked(blog);
        }

        final ScrollResult scrollResult = new ScrollResult();
        scrollResult.setList(blogs);
        scrollResult.setOffset(os);
        scrollResult.setMinTime(minTime);
        return Result.ok(scrollResult);
    }
}
