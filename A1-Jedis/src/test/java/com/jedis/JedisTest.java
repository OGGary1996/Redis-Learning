package com.jedis;


import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/*
* 本测试方法演示Jedis基本操作，不使用连接池，而是直接创建Jedis实例
* 注意：这种方式不适合生产环境，仅用于学习和测试目的
*     1. 频繁创建和关闭Jedis实例会带来非常大的性能开销
*     2. Jedis实例不是线程安全的，不能在多线程环境下共享使用
*     3. 如果要使用Jedis，推荐使用连接池，详见JedisPoolTest.java
* */

public class JedisTest {
    private Jedis jedis;

    /*
    * @Before：每一个测试方法前都创建新的Jedis实例
    */
    @BeforeEach
    void setUpJedis(){
        // 1. 创建Jedis实例,指定redis的ip和端口
        jedis = new Jedis("127.0.0.1", 6379);
        // 2. 如果redis设置了密码，需要认证
        jedis.auth("050715");
        // 3. 选择数据库，默认是0
        jedis.select(0);

        // 以上步骤可以简化为直接使用连接串，redis://:password@host:port/0
        // Jedis jedis = new Jedis("redis://:050715@127.0.0.1:6379/0");
    }

    /*
    * @AfterEach: 每一个测试方法完毕之后都关闭Jedis实例
    * */
    @AfterEach
    void shutdownJedis(){
        if (jedis != null){
            jedis.close();
        }
    }

    // 测试方法
    // 1. 测试String类型的操作
    @Test
    void testString(){
        // 1，设置key-value,方法参数为(key,value)
        jedis.set("name","Gary");
            // 设置多个key-value,方法参数为(key1,value1,key2,value2,...)
        jedis.mset("age","28","gender","Male");
        // 2. 获取key-value
        String name = jedis.get("name");
        System.out.println("name: " + name);
        // 3. 获取多个key-value,方法返回值为List<String>
        String otherValues = jedis.mget("age","gender").toString();
        System.out.println("otherValues: " + otherValues);
    }
    // 2. 测试Hash类型的操作
    @Test
    void testHash(){
        // 1. 设置hash，方法参数为Map<String,String>
        Map<String,String> userMap = new HashMap<>();
        userMap.put("name","Gary");
        userMap.put("age","28");
        userMap.put("gender","Male");
        jedis.hset("user",userMap);
        // 2. 获取hash，方法参数为key和field，返回值为String
        String userName = jedis.hget("user", "name");
        System.out.println("userName: " + userName);
        // 3. 同时获取多个field的value，方法参数
        String otherValues = jedis.hmget("user", "age", "gender").toString();
        System.out.println("otherValues: " + otherValues);

        // 4. 可以同时获取所有的field和value，返回值为Map<String,String>
        Map<String, String> userMapResult = jedis.hgetAll("user");
        System.out.println("userMapResult: " + userMapResult);

        // 5. 也可以获取所有的field，返回值为Set<String>，和所有的value，返回值为List<String>
        Set<String> userFields = jedis.hkeys("user");
        System.out.println("userFields: " + userFields);
        List<String> userValues = jedis.hvals("user");
        System.out.println("userValues: " + userValues);

    }
}
