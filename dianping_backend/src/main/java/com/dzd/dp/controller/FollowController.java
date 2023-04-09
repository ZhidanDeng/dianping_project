package com.dzd.dp.controller;


import com.dzd.dp.dto.Result;
import com.dzd.dp.service.IFollowService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/follow")
public class FollowController {

    @Autowired
    private IFollowService followService;


    @PutMapping("/{id}/{isFollow}")
    public Result follow(@PathVariable("id")Long followUserId,@PathVariable("isFollow") Boolean isFollow){
        return followService.follow(followUserId,isFollow);
    }

    @GetMapping("/or/not/{id}")
    public Result isFollowed(@PathVariable("id")Long followUserId){
        return followService.isFollowed(followUserId);
    }
    @GetMapping("/common/{id}")
    public Result getCommonFollows(@PathVariable("id")Long id){
        return followService.getCommonFollows(id);
    }
}
