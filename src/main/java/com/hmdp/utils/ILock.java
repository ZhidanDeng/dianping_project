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

    boolean tryLock(long timeoutSec, TimeUnit unit);

    void unLock();
}