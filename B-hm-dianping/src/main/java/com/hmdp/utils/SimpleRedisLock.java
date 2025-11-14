package com.hmdp.utils;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


/*
* 本类是分布式锁的Redis实现方式，目前是简单实现：获取锁和释放锁
* */
public class SimpleRedisLock implements ILock {
    private static final String KEY_PREFIX = "lock:";

    /** 唯一标识：UUID + 线程ID，保证不同服务实例不同线程都不会冲突 */
    private final String ownerId = UUID.randomUUID().toString() + "-" + Thread.currentThread().threadId();

    /* 控制续租线程是否继续运行的flag */
    private volatile boolean continueRenew = false;
    /* 续租的线程 */
    private static final ScheduledExecutorService RENEW_EXECUTOR = Executors.newSingleThreadScheduledExecutor();

    /* Lua脚本初始化 */
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("./luaScript/unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    /* key */
    private String name;
    private StringRedisTemplate stringRedisTemplate;

    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /*
    * 看门狗线程，负责线程的续租
    * 时间间隔：timeoutSec/3 秒
    * 流程：
    *  1. 固定时间检查当前线程是否还持有线程锁
    *  2. 如果持有锁，则使用Redis的expire命令续租
    *  3. 如果不持有锁，则停止续租线程
    * */
    private void scheduleRenewal(String key, long timeoutSec) {
        long period = timeoutSec / 3;
        // 通过scheduled线程池定时执行续租任务
        // scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit),表示在initialDelay时间后开始执行command任务，然后每隔period时间重复执行
        RENEW_EXECUTOR.scheduleAtFixedRate(()-> {
            // 判断是否继续续租
            if (!continueRenew) { // 如果锁已经被释放，则不需要续租
                return;
            }
            // 判断当前线程是否还持有锁，虽然无法删除其他线程的锁，但是可以做双重检查，防止误续租
            String currentOwnerId = stringRedisTemplate.opsForValue().get(key);
            if (!currentOwnerId.equals(ownerId)) { // 如果不是当前线程的锁，也不需要续期
                continueRenew = false; // 停止续租线程
                return;
            }
            // 如果以上条件判断都通过，则说明：1. 锁还没有释放，2. 锁还在当前线程手中，可以续租
            stringRedisTemplate.expire(key, timeoutSec, TimeUnit.SECONDS);
        }, period, period, TimeUnit.SECONDS);
    }

    /*
    * 获取锁方法
    * @param timeoutSec 获取锁的超时时间，单位秒
    * @return 是否获取锁成功
    * 流程：
    *  1. 使用Redis的setnx命令尝试设置键值对，如果键不存在则设置成功，表示获取锁成功
    *  2. 如果键已经存在，表示锁已被其他客户端持有，获取锁失败
    * */
    @Override
    public boolean tryLock(long timeoutSec) {
        String key = KEY_PREFIX + name;
        Boolean isSuccess = stringRedisTemplate.opsForValue().setIfAbsent(key, ownerId, timeoutSec, TimeUnit.SECONDS);
        if(Boolean.FALSE.equals(isSuccess)) { // 获取锁失败
            return false;
        }
        // 获取锁成功，启动续租线程
        continueRenew = true;
        scheduleRenewal(key, timeoutSec);
        return true;
    }

    @Override
    public void unlock() {
        // 判断锁的value是否是当前线程的id，如果是则删除锁，不是则无法删除锁，防止误删其他线程的锁
        String key = KEY_PREFIX + name;
        // 由于启用了续租线程，锁不会过期，所以value不可能为null，无须判断是否为null
        // 但是需要修改continueRenew为false，停止续租线程
        continueRenew = false;

//        // 比较value和threadId是否相等
//        String currentOwner = stringRedisTemplate.opsForValue().get(key);
//        if (currentOwner.equals(ownerId)) { // 是当前线程的锁，删除
//            stringRedisTemplate.delete(key);
//        }

        // 使用Lua脚本实现释放锁的原子操作
        // 使用execute方法执行Lua脚本
        // 参数：脚本对象、key列表、参数列表
        stringRedisTemplate.execute(
                UNLOCK_SCRIPT,
                Collections.singletonList(key),
                ownerId
        );
    }
}
