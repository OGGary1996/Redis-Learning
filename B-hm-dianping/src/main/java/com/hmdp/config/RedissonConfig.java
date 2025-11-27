package com.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

/*
* Redisson配置类
* */
@Configuration
public class RedissonConfig {
    @Value("${spring.data.redis.host}")
    private String host;
    @Value("${spring.data.redis.port}")
    private Integer port;
    @Value("${spring.data.redis.database}")
    private Integer database;
    @Value("${spring.data.redis.password}")
    private String password;

    @Bean
    public RedissonClient redissonClient() throws IOException {
        Config config = new Config();
        String redisAddress = "redis://" + host + ":" + port;
        config.useSingleServer()
                .setAddress(redisAddress)
                .setDatabase(database)
                .setPassword(password)
                .setConnectionMinimumIdleSize(1)
                .setConnectionPoolSize(10);
        config.setThreads(4);
        config.setNettyThreads(4);

        return Redisson.create(config);
    }
}
