package com.hmdp.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIdWorker {

    // 自定义纪元时间：2022-01-01 00:00:00
    private static final long BEGIN_TIMESTAMP = LocalDateTime.of(2022, 1, 1, 0, 0, 0).toEpochSecond(ZoneOffset.UTC);

    // 时间格式器
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy:MM:dd");

    // 低位序列号位数
    private static final int COUNT_BITS = 32;

    // Redis模板对象
    private final StringRedisTemplate stringRedisTemplate;
    @Autowired
    public RedisIdWorker(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    // 主方法：生成全局唯一ID
    public long nextId(String keyPrefix) {
        // 1. 生成时间戳，获取当前时间戳，减去自定义纪元时间，精确到秒
        long now = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);
        long timestamp = now - BEGIN_TIMESTAMP;

        // 2. 构造当天计数器key = "icr:" + keyPrefix + ":" + yyyyMMdd
        String today = LocalDate.now(ZoneOffset.UTC).format(DATE_TIME_FORMATTER);
        String counterKey = "icr:" + keyPrefix + ":" + today;

        // 3. 自增获取序列号
        long counter = stringRedisTemplate.opsForValue().increment(counterKey);
        // 设置key的过期时间为2天
        stringRedisTemplate.expire(counterKey, Duration.ofDays(2));

        // 4. 拼接并返回全局唯一ID
        return (timestamp << COUNT_BITS) | counter;

    }
}
