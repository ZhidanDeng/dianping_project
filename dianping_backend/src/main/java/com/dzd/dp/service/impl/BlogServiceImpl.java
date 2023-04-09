package com.dzd.dp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dzd.dp.dto.Result;
import com.dzd.dp.dto.ScrollResult;
import com.dzd.dp.dto.UserDTO;
import com.dzd.dp.entity.Blog;
import com.dzd.dp.entity.Follow;
import com.dzd.dp.entity.User;
import com.dzd.dp.mapper.BlogMapper;
import com.dzd.dp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dzd.dp.service.IFollowService;
import com.dzd.dp.service.IUserService;
import com.dzd.dp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.dzd.dp.constant.Constant.BLOG_LIKED_KEY;
import static com.dzd.dp.utils.SystemConstants.MAX_PAGE_SIZE;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author dzd
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {


    @Autowired
    private IUserService userService;
    @Autowired
    private StringRedisTemplate template;
    @Autowired
    private IFollowService followService;


    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog ->{
            queryBlogUser(blog);
            isBlogLiked(blog);
        });
        return Result.ok(records);
    }

    @Override
    public Result queryBlogById(Long id) {
        Blog blog = getById(id);
        if (blog==null){
            return Result.fail("笔记不存在");
        }
        isBlogLiked(blog);
        queryBlogUser(blog);
        return Result.ok(blog);
    }

    private void isBlogLiked(Blog blog) {
        UserDTO user = UserHolder.getUser();
        if (user == null){
            return;
        }
        Long userId = user.getId();
        String key = BLOG_LIKED_KEY+blog.getId();
        Double score = template.opsForZSet().score(key, userId.toString());
        blog.setIsLike(score!=null);
    }

    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }

    @Override
    public Result likeBlog(Long id) {
        Long userId = UserHolder.getUser().getId();
        String key = BLOG_LIKED_KEY+id.toString();
        Double score = template.opsForZSet().score(key, userId.toString());
        if (score==null){
            boolean isSuccess = update().setSql("liked = liked+1").eq("id", id).update();
            if (isSuccess){
                template.opsForZSet().add(key,userId.toString(),System.currentTimeMillis());
            }
        }else {
            boolean isSuccess = update().setSql("liked = liked-1").eq("id", id).update();
            if (isSuccess){
                template.opsForZSet().remove(key,userId.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result queryBlogLikes(Long id) {
        String key = BLOG_LIKED_KEY+id.toString();
        Set<String> likesTopFive = template.opsForZSet().range(key, 0, 4);
        if (likesTopFive==null || likesTopFive.isEmpty()){
            return Result.ok();
        }
        //解析userId
        List<Long> userIds = likesTopFive.stream().map(Long::valueOf).collect(Collectors.toList());
        String idStr = StrUtil.join(",", userIds);
        List<UserDTO> userDTOS = userService.query()
                .in("id",userIds)
                .last("order by field(id,"+idStr+")")
                .list()
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());

        return Result.ok(userDTOS);
    }

    @Override
    public Result queryBlogByUserId(Integer current, Long id) {
        Page<Blog> blogs = query().eq("user_id", id).page(new Page<>(current, MAX_PAGE_SIZE));
        List<Blog> records = blogs.getRecords();
        return Result.ok(records);
    }

    @Override
    public Result saveBlog(Blog blog) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 保存探店博文
        boolean isSuccess = save(blog);
        if (!isSuccess){
            return Result.fail("发表笔记失败！");
        }
        //查询作者粉丝
        List<Follow> follows = followService.query().eq("follow_user_id", user.getId()).list();
        for(Follow f: follows){
            //推送笔记id给所有粉丝
            Long fansId = f.getUserId();
            String key = "feed:"+fansId;
            template.opsForZSet().add(key,blog.getId().toString(),System.currentTimeMillis());
        }
        // 返回id
        return Result.ok(blog.getId());
    }

    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        Long userId = UserHolder.getUser().getId();
        String key = "feed:"+userId;
        //查询收件箱zrevrangebyscore key max min limit offset count
        Set<ZSetOperations.TypedTuple<String>> typedTuples = template.opsForZSet()
                .reverseRangeByScoreWithScores(key, 0, max, offset, 2);
        if (typedTuples==null || typedTuples.isEmpty()){
            return Result.ok();
        }
        //解析数据：blogId,minTime(时间戳),offset
        ArrayList<Long> ids = new ArrayList<>(typedTuples.size());
        long minTime = 0;
        int new_offset = 1;
        for (ZSetOperations.TypedTuple<String> tuple : typedTuples) {
            ids.add(Long.valueOf(tuple.getValue()));
            long time = tuple.getScore().longValue();
            if (time==minTime){
                new_offset++;
            }else {
                minTime = time;
                new_offset = 1;
            }
        }
        String idStr = StrUtil.join(",", ids);
        List<Blog> blogs = query().in("id", ids).last("order by field(id," + idStr + ")").list();
        for(Blog b: blogs){
            isBlogLiked(b);
            queryBlogUser(b);
        }
        ScrollResult result = new ScrollResult();
        result.setList(blogs);
        result.setMinTime(minTime);
        result.setOffset(new_offset);
        return Result.ok(result);
    }
}
