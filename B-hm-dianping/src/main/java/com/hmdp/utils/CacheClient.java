package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Slf4j
@Component
public class CacheClient {
    // 逻辑过期方法的缓存重建线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(5);
    private final StringRedisTemplate stringRedisTemplate;
    @Autowired
    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }


    /*
    * 普通set方法
    * @param key
    * @param value
    * @param time
    * @param unit
    * 用于存储普通缓存数据，并且设置TTL
    * */
    public void set(String key, Object value, Long time, TimeUnit timeUnit) {
        // 将value转换为JSON字符串存储
        String valueStr = JSONUtil.toJsonStr(value);
        stringRedisTemplate.opsForValue().set(key, valueStr , time, timeUnit);
    }

    /*
    * setWithLogicalExpire方法
    * @param key
    * @param value
    * @param time
    * @param unit
    * 用于存储带有逻辑过期时间的缓存数据
    * 注意：需要通过RedisData对象来封装数据和逻辑过期时间
    * */
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit timeUnit) {
        // 创建RedisData对象
        RedisData redisData = new RedisData();
        redisData.setData(value);
        // 设置逻辑过期时间
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(timeUnit.toSeconds(time)));
        // 将RedisData对象转换为JSON字符串存储
        String redisDataStr = JSONUtil.toJsonStr(redisData);
        stringRedisTemplate.opsForValue().set(key, redisDataStr);
    }

    /*
    * queryWithPassThrough方法
    *
    * 用于查询缓存数据与数据库都不存在时，设置空值缓存，解决缓存穿透问题
    * 注意：
    *  1. 无法确定返回值value的类型，需要指定Class<R> type参数
    *  2. 无法确定数据库查询时的id类型，需要指定ID类型的泛型参数
    *  3. 无法确定查询哪个数据库，需要传递查询数据库的逻辑函数作为参数 Function<ID, R> dbFallback
    * */
    public <R, ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit timeUnit) {
        // 1. 拼接key，查询缓存
        String key = keyPrefix + id;
        log.debug("Querying cache with key: {}", key);
        String valueStr = stringRedisTemplate.opsForValue().get(key);
        // 2. 判断是否有值，有值直接饭回
        if (StrUtil.isNotBlank(valueStr)) {
            log.debug("Cache hit for key: {}", key);
            return JSONUtil.toBean(valueStr, type);
        }
        // 3. 判断是否为空值缓存，返回空值缓存
        // 注意：此时如果不是null，就是空字符串""
        if (valueStr != null) {
            log.debug("Cache hit for key (empty value): {}", key);
            throw new RuntimeException("Result Not Found!");
        }

        // 4. 缓存未命中，查询数据库
        log.debug("Cache miss for key: {}, querying database...", key);
        R result = dbFallback.apply(id);
        // 5. 判断数据库查询结果, 如果查到，写入缓存；查不到，写入空值缓存
        if(result == null){
            log.debug("Database miss for id: {}, caching empty value", id);
            // 写入空值缓存，设置较短的TTL，防止缓存穿透
            this.set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            throw new RuntimeException("Result Not Found!");
        }
        log.debug("Database hit for id: {}, caching result", id);
        // 写入缓存，设置正常的TTL,调用正常的set方法
       this.set(key, result, time, timeUnit);
        return result;
    }

    /*
    * queryWithLogicalExpire方法
    * 用于查询时使用逻辑过期时间解决缓存击穿问题
    * 注意：需要声明线程池用于缓存重建
    * */
    public <R, ID> R queryWithLogicalExpire(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit timeUnit) {
        // 1. 拼接key，查询缓存
        String key = keyPrefix + id;
        log.debug("Querying cache with key: {}", key);
        String redisDataStr = stringRedisTemplate.opsForValue().get(key);
        // 2. 注意：这里不需要考虑没有命中缓存的情况，因为使用逻辑过期时间的方式一定会命中缓存，只是缓存可能过期
        if (StrUtil.isBlank(redisDataStr)) {
            log.debug("Cache miss for key: {}", key);
            throw new RuntimeException("Result Not Found!");
        }
        // 3. 命中缓存，先将JSON反序列化为RedisData对象
        log.debug("Cache hit for key: {}, checking expiration...", key);
        RedisData redisData = JSONUtil.toBean(redisDataStr, RedisData.class);
        // 4. 判断是否过期
        LocalDateTime expireTime = redisData.getExpireTime();
        R result = JSONUtil.toBean(JSONUtil.toJsonStr(redisData.getData()), type);
        if (expireTime.isAfter(LocalDateTime.now())) { // 未过期，直接返回
            log.debug("Cache not expired for key: {}", key);
            return result;
        }
        // 5. 如果已经过期，通过异步线程重建缓存
        log.debug("Cache expired for key: {}, attempting to rebuild cache...", key);
        // 5.1 获取互斥锁
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        if (!tryLock(lockKey)) { // 5.2 如果未获取到锁，则直接返回过期的商铺信息
            log.debug("Failed to acquire lock for key: {}, returning expired data", key);
            return result;
        }
        // 5.3 如果获取到锁，则开启独立线程重建缓存
        log.debug("Acquired lock for key: {}, rebuilding cache in separate thread", key);
        CACHE_REBUILD_EXECUTOR.submit( () -> {
            try {
                // 调用封装的内部方法，重建缓存
                R newResult = dbFallback.apply(id);
                this.setWithLogicalExpire(key, newResult, time, timeUnit);
            }catch (Exception e) {
                log.error("Error rebuilding cache for key: {}", key, e);
            }finally {
                // 释放锁
                unlock(lockKey);
            }
        });
        // 5.4 返回过期的商铺信息
        return result;

    }
    /*
    * 获取锁 & 释放锁
    * 本质上是通过Redis的setnx命令实现的互斥锁
    * */
    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", RedisConstants.LOCK_SHOP_TTL, TimeUnit.SECONDS);
        log.debug("获取锁{}，结果：{}", key, flag);
        return BooleanUtil.isTrue(flag);
    }
    private void unlock(String key) {
        stringRedisTemplate.delete(key);
        log.debug("释放锁{}", key);
    }


}
