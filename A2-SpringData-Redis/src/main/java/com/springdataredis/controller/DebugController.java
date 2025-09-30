package com.springdataredis.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/debug")
public class DebugController {
    @Value("${spring.data.redis.username:}")
    private String redisUser;
    @Value("${spring.data.redis.password:}")
    private String redisPass;

    @GetMapping("/redis-config")
    public String debugConfig() {
        return "user=" + redisUser + ", pass=" + redisPass;
    }
}
