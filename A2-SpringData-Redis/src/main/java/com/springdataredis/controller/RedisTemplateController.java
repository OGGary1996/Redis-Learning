package com.springdataredis.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("redisTemplate")
public class RedisTemplateController {
    private final RedisTemplate<Object,Object> redisTemplate;
    @Autowired
    public RedisTemplateController(RedisTemplate<Object, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /*
    * set 值
    * */
    @GetMapping("/set")
    public String setKey(){
        redisTemplate.opsForValue().set("name","Gary");
        return "Set key success";
    }
    /*
    * get 值
    * */
    @GetMapping("/get")
    public String getKey(){
        return (String) redisTemplate.opsForValue().get("name");
    }
}
