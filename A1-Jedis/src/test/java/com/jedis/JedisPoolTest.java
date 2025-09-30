package com.jedis;

import com.jedis.config.JedisConnectionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.List;

public class JedisPoolTest {
    private static Jedis jedis;

    @BeforeEach
    void setUp(){
        jedis = JedisConnectionFactory.getJedis();
    }

    @AfterEach
    void shutdownPool(){
        if (jedis != null){
            jedis.close();
        }
    }

    @Test
    // 测试List类型的操作
    void testList(){
        // 1. 添加元素
        jedis.lpush("myList","a","b","c","d","e","A");
        // 2. 删除元素
        System.out.println("Pop out: " + jedis.lpop("myList"));
        // 3. 获取元素
        List<String> myList = jedis.lrange("myList", 0, -1);
        System.out.println("myList: " + myList.toString());
        // 4. 获取单个元素
        String element = jedis.lindex("myList", 2);
        System.out.println("Element at index 2: " + element);
        // 5. 获取List长度
        Long size = jedis.llen("myList");
        System.out.println("Size of myList: " + size);
    }

    @Test
    // 测试Set类型的操作
    void testSet(){
        // 1. 添加元素
        jedis.sadd("mySet","a","b","c","d","e","A");
        // 2. 删除元素
        jedis.srem("mySet","A");
        // 3. 获取所有元素
        System.out.println("mySet: " + jedis.smembers("mySet"));
        // 4. 判断元素是否存在
        System.out.println("Is 'a' in mySet? " + jedis.sismember("mySet", "a"));
        // 5. 获取Set长度
        System.out.println("Size of mySet: " + jedis.scard("mySet"));
    }
}
