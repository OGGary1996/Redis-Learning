package com.springdataredis.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;

@Configuration
public class RedisConfig {
    /*
    * 替换默认的 RedisTemplate，需要连接池作为参数
    *
    * */
    @Bean
    public RedisTemplate<String,Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        // 1. 创建 RedisTemplate 对象
        RedisTemplate<String,Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // 2. 新建想要替换的序列化器,GenericJackson2JsonRedisSerializer
        GenericJackson2JsonRedisSerializer genericJackson2JsonRedisSerializer = new GenericJackson2JsonRedisSerializer();

        // 3. 替换默认的序列化器
            // 3.1 key 的序列化器,采用string序列化器
        template.setKeySerializer(RedisSerializer.string());
            // 3.2 value 的序列化器
        template.setValueSerializer(genericJackson2JsonRedisSerializer);
            // 3.3 hash key 的序列化器.,采用string序列化器
        template.setHashKeySerializer(RedisSerializer.string());
            // 3.4 hash value 的序列化器
        template.setHashValueSerializer(genericJackson2JsonRedisSerializer);
        // 4. 返回
        return template;
    }
}
