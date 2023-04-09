package com.dzd.dp.utils;

/**
 * Created with IntelliJ IDEA.
 *
 * @Author: DZD
 * @Date: 2023/03/31/21:33
 * @Description:
 */
public interface ILock {
     boolean tryLock(long timeoutSec);
     void unLock();
}
