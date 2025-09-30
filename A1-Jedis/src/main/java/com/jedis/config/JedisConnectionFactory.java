package com.jedis.config;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.time.Duration;

public class JedisConnectionFactory {
    private static final JedisPool jedisPool;
    static {
        // 1.创建连接池配置对象
        JedisPoolConfig config = new JedisPoolConfig();
        // 2. 配置配置对象
            // 2.1 最大连接数
        config.setMaxTotal(10);
            // 2.2 最大空闲连接数
        config.setMaxIdle(10);
            // 2.3 最小空闲连接数
        config.setMinIdle(3);
            // 2.4 获取连接时的最大等待时间
        config.setMaxWait(Duration.ofMinutes(1));
        // 3. 创建Jedis连接池对象
        jedisPool = new JedisPool(config,"redis://:050715@127.0.0.1:6379/0");
    }

    // 4. 提供获取Jedis实例的方法
    public static Jedis getJedis(){
        return jedisPool.getResource();
    }
}
