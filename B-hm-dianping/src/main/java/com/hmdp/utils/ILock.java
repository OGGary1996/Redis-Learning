package com.hmdp.utils;

/*
* 本接口提供分布式锁的基本操作定义
* */
public interface ILock {

    /*
    * 获取锁方法
    * @param timeoutSec 获取锁的超时时间，单位秒
    * @return 是否获取锁成功
    * */
    boolean tryLock(long timeoutSec);

    /*
    * 释放锁方法
    * */
    void unlock();
}
