package com.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

/*
* Redisson配置类
* */
@Configuration
public class RedissonConfig {
    @Bean
    public RedissonClient redissonClient() throws IOException {
        // 读取默认的redisson配置文件redisson.yaml
        Config config = Config.fromYAML(
                RedissonConfig.class.getClassLoader().getResource("redisson.yaml")
        );
        return Redisson.create(config);
    }
}
