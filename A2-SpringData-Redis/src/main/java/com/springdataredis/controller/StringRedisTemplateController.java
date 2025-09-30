package com.springdataredis.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.springdataredis.pojo.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/string-redis-template-demo")
public class StringRedisTemplateController {
    private final StringRedisTemplate stringRedisTemplate;
    private static final ObjectMapper objectMapper = new ObjectMapper();
    @Autowired
    public StringRedisTemplateController(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @GetMapping("/set-object")
    public String setObject() throws JsonProcessingException {
        // 1. 创建对象
        User user = User.builder()
                .id(1L)
                .name("Gary")
                .age(18)
                .gender("Male")
                .build();
        // 2. 手动序列化
        String userStr = objectMapper.writeValueAsString(user);
        // 3. 存储
        stringRedisTemplate.opsForValue().set("User", userStr);
        return "Set object success";
    }

    @GetMapping("/get-object")
    public User getObject() throws JsonProcessingException {
        // 1.读取字符串
        String userStr = stringRedisTemplate.opsForValue().get("User");
        // 2.手动反序列化
        User user = objectMapper.readValue(userStr, User.class);
        return user;
    }

    /*
    * Hash类型操作
    * */
    @GetMapping("/set-hash")
    public String setHash(){
        // 单个put,等同于 HSET key field value
        stringRedisTemplate.opsForHash().put("user:1","name","Gary");
        stringRedisTemplate.opsForHash().put("user:1","age","18");
        // 批量put,等同于 HMSET key field1 value1 field2 value2 ...
        stringRedisTemplate.opsForHash().putAll("user:2", Map.of("name","Mike","age","20"));

        return "Set hash success";
    }
    @GetMapping("/get-hash")
    public List<Map<Object,Object>> getHash(){
        // 获取单个值,等同于 HGET key field
        Object name = stringRedisTemplate.opsForHash().get("user:1", "name");
        Object age = stringRedisTemplate.opsForHash().get("user:1", "age");
        Map<Object, Object> user1 = Map.of("name", name, "age", age);
        // 获取所有的简直对，等同于 HGETALL key
        Map<Object, Object> user2 = stringRedisTemplate.opsForHash().entries("user:2");

        return List.of(user1,user2);
    }

    /*
    * List类型操作
    * */
    @GetMapping("/set-list")
    public String setList(){
        // 从左侧插入,等同于 LPUSH key value1 value2 ...
        stringRedisTemplate.opsForList().leftPush("users","Gary");
        // 从右侧插入,等同于 RPUSH key value1 value2 ...
        stringRedisTemplate.opsForList().rightPush("users","Mike");
        // 多个值插入,等同于 LPUSH key value1 value2 ... 或 RPUSH key value1 value2 ...
        stringRedisTemplate.opsForList().leftPushAll("users","Bob","Alice","Tom");
        // 出列,等同于 LPOP key 或 RPOP key
        String user1 = stringRedisTemplate.opsForList().leftPop("users");
        String user2 = stringRedisTemplate.opsForList().rightPop("users");

        return "Set list success";
    }
    @GetMapping("/get-list")
    public List<String> getList(){
        // 获取列表长度,等同于 LLEN key
        Long size = stringRedisTemplate.opsForList().size("users");
        // 获取指定范围的元素,等同于 LRANGE key start end
        List<String> users = stringRedisTemplate.opsForList().range("users", 0, size);

        return users;
    }

    /*
    * 操作 Set类型
    * */
    @GetMapping("/set-set")
    public String setSet(){
        // 1. 添加元素,等同于 SADD key member1 member2 ...
        stringRedisTemplate.opsForSet().add("fruits","Apple","Banana","Orange,Grape");
        // 2. 移除元素,等同于 SREM key member1 member2 ...
        stringRedisTemplate.opsForSet().remove("fruits","Grape");
        // 3. 判断是否存在,等同于 SISMEMBER key member
        Boolean isMember = stringRedisTemplate.opsForSet().isMember("fruits", "Banana");
        System.out.println("Is Banana a member of fruits set? " + isMember);
        return "Set set success";
    }
    @GetMapping("/get-set")
    public Set<String> getSet() {
        // 获取所有元素,等同于 SMEMBERS key
        return stringRedisTemplate.opsForSet().members("fruits");
    }

    /*
    * 操作 Sorted Set类型
    * */
    @GetMapping("/set-sorted-set")
    public String setSortedSet(){
        // 1. 添加元素,等同于 ZADD key score1 member1 [score2 member2 ...]
        stringRedisTemplate.opsForZSet().add("rankings","Alice",100.0);
        stringRedisTemplate.opsForZSet().add("rankings","Bob",95.5);
        stringRedisTemplate.opsForZSet().add("rankings","Charlie",98.0);
        // 2. 移除元素,等同于 ZREM key member1 member2 ...
        stringRedisTemplate.opsForZSet().remove("rankings","Bob");
        // 3. 获取排名,等同于 ZRANK key member
        Long rank = stringRedisTemplate.opsForZSet().rank("rankings", "Charlie");
        System.out.println("Charlie's rank: " + (rank != null ? rank + 1 : "Not found"));
        return "Set sorted set success";
    }
    @GetMapping("/get-sorted-set")
    public Set<String> getSortedSet(){
        // 获取指定范围的元素,等同于 ZRANGE key start end [WITHSCORES]
        return stringRedisTemplate.opsForZSet().range("rankings", 0, -1);
    }
}
