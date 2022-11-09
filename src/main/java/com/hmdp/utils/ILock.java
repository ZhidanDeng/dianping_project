package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;

import java.util.concurrent.TimeUnit;

/**
 * @Description Redis锁
 * @Version 1.0.0
 * @Date 2022/11/9
 * @Author wandaren
 */
public interface ILock {
    /**
     * 获取锁
     * @param timeoutSec  超时时间
     * @param unit  时间单位
     * @return  是否成功获取到锁
     */
    boolean tryLock(long timeoutSec, TimeUnit unit);

    /**
     * 释放锁
     */
    void unLock();
}