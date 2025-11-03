package com.itheima.controller;

import com.itheima.entity.User;
import com.itheima.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user")
@Slf4j
@CacheConfig(cacheNames = "cacheDemo-user")
// 最终的缓存的Key的组成：cacheNames + key
public class UserController {

    @Autowired
    private UserMapper userMapper;

    /*
    * 新增User
    * 写入缓存：@CachePut(key = "#user.id")
    * */
    @PostMapping
    @CachePut(key = "#user.id")
    public User save(@RequestBody User user){
        userMapper.insert(user);
        return user;
    }

    /*
    * 删除单个User
    * 删除缓存：@CacheEvict(key = "#id")
    * */
    @DeleteMapping
    @CacheEvict(key = "#id")
    public void deleteById(Long id){
        userMapper.deleteById(id);
    }

    /*
    * 删除所有User
    * 删除所有缓存：@CacheEvict(allEntries = true)
    * */
	@DeleteMapping("/delAll")
    @CacheEvict(allEntries = true)
    public void deleteAll(){
        userMapper.deleteAll();
    }

    /*
    * 查询单个User
    * 查询缓存：@Cacheable(key = "#id")
    * */
    @GetMapping
    @Cacheable(key = "#id")
    public User getById(Long id){
        User user = userMapper.getById(id);
        return user;
    }

}
