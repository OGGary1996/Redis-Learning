package com.hmdp.utils;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class RedisIdWorkerTest {

    @Autowired
    private RedisIdWorker redisIdWorker;

    /*
    * 压力测试，通过多线程生成大量ID，验证RedisIdWorker的性能和正确性
    * */
    @Test
    void testNextId() throws InterruptedException {
        // 线程总数量
        int threadCount = 32;
        // 每个线程生成ID的数量
        int idsPerThread = 500;
        // 通过线程池创建固定数量的线程
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        // 通过CountDownLatch等待所有线程执行完毕
        CountDownLatch latch = new CountDownLatch(threadCount);
        // 创建ConcurrentHashSet用于存储生成的ID，确保ID唯一性，总大小为threadCount * idsPerThread
        Set<Long> idSet = ConcurrentHashMap.newKeySet(threadCount * idsPerThread);

        // 启动多个线程生成ID
        // 记录时间
        long start = System.currentTimeMillis();
        for (int threadIndex = 0 ; threadIndex < threadCount ; threadIndex++) { // 外层循环，创建多个线程
            pool.submit( () -> {
                for (int i = 0 ; i < idsPerThread ; i++){ // 内层循环，每个线程生成多个ID
                    long id = redisIdWorker.nextId("order");
                    idSet.add(id); // 将生成的ID添加到集合中
                }
                // 线程执行完毕，计数器减一
                latch.countDown();
            });
        }
        // 主线程等待所有子线程执行完毕
        latch.await();
        // 关闭线程池
        if (!pool.isShutdown()) {
            pool.shutdown();
        }

        // 验证生成的ID数量是否正确且唯一
        long end = System.currentTimeMillis();
        System.out.println("生成ID数量：" + idSet.size());
        System.out.println("总耗时：" + (end - start) + "ms");
        assertEquals(threadCount * idsPerThread, idSet.size());
    }
}