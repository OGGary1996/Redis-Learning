package com.hmdp;

import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.TimeUnit;

/*
* 测试Redisson中的分布式锁功能
* */
@SpringBootTest
public class RedissonTest {
    // 初始化RedissonClient对象
    private final RedissonClient redissonClient;
    @Autowired
    public RedissonTest(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    /*
    * 测试RLock，可重入锁
    * 在同一个线程中，可以多次获取同一把锁
    * */
    @Test
    void rLockTest() throws InterruptedException {
        // 获取锁对象
        RLock lock = redissonClient.getLock("test:RLock");
        // 加锁, 参数：最大等待时间1秒，锁自动释放时间10秒；在1秒之内反复尝试获取锁，锁的自动释放时间10秒，不会触发Watchdog机制
        boolean isLock = lock.tryLock(1, 10 , TimeUnit.SECONDS);
        // 判断是否加锁成功
        if (isLock) {
            //如果获取成功
            try {
                System.out.println("获取锁成功，执行业务逻辑");
            }finally {
                //释放锁
                lock.unlock();
            }
        }else {
            //如果获取失败
            System.out.println("获取锁失败，稍后重试");
        }
    }
}
