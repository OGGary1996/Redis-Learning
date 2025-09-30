package com.springdataredis.controller;

import com.springdataredis.pojo.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/redisTemplate-object")
public class RedisTemplateObjectController {
    private final RedisTemplate<String, Object> redisTemplate;
    @Autowired
    public RedisTemplateObjectController(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /*
    * set 对象
    * */
    @GetMapping("/set-object")
    public String setObject(){
        User user = User.builder()
                .id(1L)
                .name("Gary")
                .age(18)
                .gender("Male")
                .build();

        redisTemplate.opsForValue().set("user", user);
        return "Set object success";
    }

    /*
    * get 对象
    * */
    @GetMapping("/get-object")
    public User getObject(){
        return (User) redisTemplate.opsForValue().get("user");
    }
}
