package com.hmdp.utils;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/*
* 本类管理用户互斥锁
* 通过ConcurrentHashMap存储用户id和对应的锁对象, 锁对象直接使用Object类的实例
* computeIfAbsent方法:
*  1. 检查map中是否存在指定key的映射关系
*  2. 如果存在, 返回对应的value
*  3. 如果不存在, 使用提供的映射函数计算一个新的value, 将其与key关联, 并返回新的value
*  4. 该方法是原子操作, 在多线程环境下可以
* */

// 由于本方式仅适用于单实例部署, 多实例部署下无法保证全局唯一锁, 因此弃用
// 优化为基于Redis的分布式锁，详见ILock.java
@Deprecated
@Component
public class UserMutexManager {
    private final ConcurrentHashMap<Long , Object> userLocks = new ConcurrentHashMap<>();

    /*
    * 获取用户锁对象
    * */
    public Object getUserLock(Long userId){
        return userLocks.computeIfAbsent(userId, k -> new Object());
    }
}
