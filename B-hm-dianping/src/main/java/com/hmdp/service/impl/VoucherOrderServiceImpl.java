package com.hmdp.service.impl;

import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.*;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * <p>
 *  服务实现类
 * </p>
 */
@Service
@Slf4j
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    /*
    * 实现方法：
    *   1. 使用自定义分布式锁UserMutexManager实现秒杀下单
    *   2. 优化为使用Redisson分布式锁实现秒杀下单
    * */
//    // private final UserMutexManager userMutexManager;
//    // 优化为分布式锁, new SimpleRedisLock
//    // 再优化为Redisson分布式锁
//    // private final StringRedisTemplate stringRedisTemplate;
//    private final SeckillVoucherServiceImpl seckillVoucherService;
//    private final RedisIdWorker redisIdWorker;
//    private final RedissonClient redissonClient;
//    @Autowired
//    public VoucherOrderServiceImpl(SeckillVoucherServiceImpl seckillVoucherService, RedisIdWorker redisIdWorker, RedissonClient redissonClient) {
//        this.seckillVoucherService = seckillVoucherService;
//        this.redisIdWorker = redisIdWorker;
//        // this.stringRedisTemplate = stringRedisTemplate;
//        this.redissonClient = redissonClient;
//    }
//
//    /*
//    * 新增秒杀订单记录
//    * */
//    @Override
//    @Transactional
//    public void seckillVoucher(Long voucherId) {
//        // 1. 获取秒杀券，来自于seckillVoucherService中
//        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
//        // 2. 判断是否在秒杀时间内
//        LocalDateTime beginTime = seckillVoucher.getBeginTime();
//        LocalDateTime endTime = seckillVoucher.getEndTime();
//        if (LocalDateTime.now().isBefore(beginTime)) { // 过早
//            throw new RuntimeException("秒杀尚未开始！");
//        }
//        if (LocalDateTime.now().isAfter(endTime)) { // 过晚
//            throw new RuntimeException("秒杀已经结束！");
//        }
//        // 3. 判断库存是否充足
//        Integer stock = seckillVoucher.getStock();
//        if (stock < 1) {
//            throw new RuntimeException("库存不足！");
//        }
//
//        // 4. 一人一单判断，防止同一用户多次下单,保证幂等性
//        // 判断一人一单 + 减库存 + 创建订单的操作需要放在同一个锁中
//        // 用户Id, 从ThreadLocal中获取当前用户
//        Long userId = UserHolder.getUser().getId();
//
//        // 优化为使用分布式锁的方式解决一人一单问题
//        // SimpleRedisLock redisLock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
//        // boolean isLock = redisLock.tryLock(20); // 尝试获取锁，1000秒超时
//
//        // 再优化为使用Redisson分布式锁
//        RLock lock = redissonClient.getLock("lock:order:" + userId);
//        try {
//            boolean isLock = lock.tryLock(10, TimeUnit.SECONDS); // 尝试获取锁，10秒之内重复尝试
//            if (!isLock){ // 获取锁失败，说明已经有一个线程在为该用户创建订单
//                throw new RuntimeException("不允许重复下单！");
//            }
//            // 获取锁成功，进入下单流程
//            // 4.1 统计当前用户的订单数量
//            Long count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
//            // 4.2 判断是否已经购买过
//            if (count > 0) {
//                throw new RuntimeException("每个用户只能购买一次！");
//            }
//            // 5. 时间判断 & 库存判断通过，扣减库存，创建订单
//            // 通过MyBatis-Plus的update方法实现扣减库存
//            boolean isSuccess = seckillVoucherService.update()
//                    .setSql("stock = stock - 1")
//                    .eq("voucher_id", voucherId)
//                    .eq("stock", stock) // 乐观锁，将stock作为version版本号，确保stock没有被其他线程修改
//                    .gt("stock", 0) // 只有gt("stock",0)成立时才更新成功（stock > 0）
//                    .update();
//            if (!isSuccess) {
//                throw new RuntimeException("库存不足！");
//            }
//            // 6. 无重复购买，可以创建订单，插入到voucher_order表中
//            VoucherOrder voucherOrder = new VoucherOrder();
//            // 订单id，采用Redis生成全局唯一id
//            long id = redisIdWorker.nextId("voucher_order");
//            voucherOrder.setId(id);
//            voucherOrder.setUserId(userId);
//            voucherOrder.setVoucherId(voucherId);
//            save(voucherOrder);
//        }catch (InterruptedException e) {
//            throw new RuntimeException("每个用户只能购买一次！");
//        }
//        finally {
//            lock.unlock(); // 释放锁
//        }
//    }




    /*
    * 优化为使用Lua脚本 + 阻塞队列 + 异步线程池的方式实现秒杀下单
    * 流程：
    *  1. 调用Lua脚本，根据结果判断是否允许下单
    *  2. 如果允许下单，将订单信息写入到阻塞队列中
    *  3. 声明线程池 & 线程执行方法；通过@PostConstruct注解，在VoucherOrderServiceImpl类初始化后，启动一个异步线程，执行下单操作
    * */
    // 1. 声明DefaultRedisScript
//    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
//    static {
//        SECKILL_SCRIPT = new DefaultRedisScript<>();
//        SECKILL_SCRIPT.setLocation(new ClassPathResource("luaScript/seckill.lua"));
//        SECKILL_SCRIPT.setResultType(Long.class);
//    }
//    // 2. 注入所需的组件
//    private final SeckillVoucherServiceImpl seckillVoucherService;
//    private final RedisIdWorker redisIdWorker;
//    private final StringRedisTemplate stringRedisTemplate;
//    @Autowired
//    public VoucherOrderServiceImpl(SeckillVoucherServiceImpl seckillVoucherService, RedisIdWorker redisIdWorker, StringRedisTemplate stringRedisTemplate) {
//        this.seckillVoucherService = seckillVoucherService;
//        this.redisIdWorker = redisIdWorker;
//        this.stringRedisTemplate = stringRedisTemplate;
//    }
//    // 3. 声明阻塞队列
//    private final BlockingQueue<VoucherOrder> scekillOrderTasks = new ArrayBlockingQueue<>(1024 * 1024);
//    // 4. 声明线程池，单线程即可
//    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();
//    // 5. 声明异步处理的线程方法，使用匿名内部类即可
//    class SeckillOrderHandler implements Runnable {
//        @Override
//        public void run() {
//            while (true) {
//                try {
//                    // 1. 从阻塞队列中获取订单信息
//                      // take()方法：如果队列为空，线程 *阻塞* 等待
//                    VoucherOrder voucherOrder = scekillOrderTasks.take();
//                    // 2. 调用具体的入库方法
//                    handleScekillOrder(voucherOrder);
//                }catch (Exception e){
//                    throw new RuntimeException(e);
//                }
//            }
//        }
//    }
//    // 5. 为了实现异步处理，使用@PostConstruct，在Bean初始化之后，启动一个异步线程，提交任务（实际的入库操作）
//    @PostConstruct
//    public void init() {
//        // 提交异步处理的线程方法
//        SECKILL_ORDER_EXECUTOR.submit(new SeckillOrderHandler());
//    }
//
//    // 6. 定义具体的入库方法
//    @Transactional
//    protected void handleScekillOrder(VoucherOrder voucherOrder) {
//        // 1. 实际扣减库存
//        Long voucherId = voucherOrder.getVoucherId();
//        seckillVoucherService.update()
//                .setSql("stock = stock - 1")
//                .eq("voucher_id",voucherId)
//                .gt("stock",0) // 只有gt("stock",0)成立时才更新成功（stock > 0）
//                .update();
//
//        // 2. 实际插入订单数据
//        save(voucherOrder);
//    }
//
//    @Override
//    public Long seckillVoucher(Long voucherId) {
//        // 构建各种KEYS[] 和 ARGV[]
//        // 1. KEYS[1] -- 库存 key：seckill:stock:{voucherId}
//        String stockKey = RedisConstants.SECKILL_STOCK_KEY + voucherId;
//        // 2. KEYS[2] -- 订单 key：seckill:order:users:{voucherId}
//        String orderUsersKey = RedisConstants.SECKILL_ORDER_USERS_KEY + voucherId;
//
//        // 3. ARGV[1] -- 用户ID,注意，数据类型应该是字符串，因为Redis中存储的都是字符串
//        String userId = UserHolder.getUser().getId().toString();
//        // 4. ARGV[2] -- VoucherID
//        String voucherIdStr = voucherId.toString();
//
//        // 执行lua脚本，并传递参数
//        Long result = stringRedisTemplate.execute(SECKILL_SCRIPT,
//                List.of(stockKey, orderUsersKey),
//                userId, voucherIdStr
//        );
//        // 解析结果
//        if (result == 1L) {
//            throw new RuntimeException("库存不足！");
//        }
//        else if (result == 2L) {
//            throw new RuntimeException("每个用户只能购买一次！");
//        }
//        // 结果解析为0L, 表示消息队列中已经成功添加了订单信息
//        // 将订单信息写入到阻塞队列中
//        VoucherOrder voucherOrder = new VoucherOrder();
//        // 订单ID, 采用Redis生成全局唯一ID
//        long orderId = redisIdWorker.nextId("voucher_order");
//        voucherOrder.setId(orderId);
//        voucherOrder.setUserId(Long.parseLong(userId));
//        voucherOrder.setVoucherId(voucherId);
//        // 将订单信息添加到阻塞队列中
//        scekillOrderTasks.add(voucherOrder);
//
//        // 返回订单ID
//        return Long.valueOf(orderId);
//    }

    /*
    * 优化为使用Lua脚本 + 基于Redis-Stream + 异步线程池的方式实现秒杀下单
    * 流程：
    *  1. 调用Lua脚本，根据结果判断是否允许下单，并且在Lua脚本中写入消息到Redis-Stream消息队列
    *  2. 如果允许下单，则调用异步线程池，从Redis-Stream中读取订单信息并处理
    *  3. 声明线程池 & 线程执行方法；通过@PostConstruct注解，在VoucherOrderServiceImpl类初始化后，启动一个异步线程，执行下单操作
    * */
    // 1. 声明DefaultRedisScript
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("luaScript/seckill_stream.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }
    // 2. 注入所需的组件
    private final SeckillVoucherServiceImpl seckillVoucherService;
    private final RedisIdWorker redisIdWorker;
    private final StringRedisTemplate stringRedisTemplate;
    @Autowired
    public VoucherOrderServiceImpl(SeckillVoucherServiceImpl seckillVoucherService, RedisIdWorker redisIdWorker, StringRedisTemplate stringRedisTemplate) {
        this.seckillVoucherService = seckillVoucherService;
        this.redisIdWorker = redisIdWorker;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    // 3. 声明消息队列的Group和Consumer
    private final String STREAM_GROUP = "g1";
    private final String STREAM_CONSUMER = "c1";

    // 4. 声明线程池，单线程即可
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();
    // 5. 声明异步处理的线程方法，使用匿名内部类即可
    class SeckillOrderHandler implements Runnable {
        @Override
        public void run() {
            while (true) {
                try {
                    // 1. 从消息队列中获取订单信息
                    // XREADGROUP GROUP g1 c1 COUNT 1 BLOCK 2000 STREAMS stream.orders > , > 表示从上次消费的位置开始消费
                    List<MapRecord<String, Object, Object>> records = stringRedisTemplate.opsForStream().read(
                            Consumer.from(STREAM_GROUP, STREAM_CONSUMER),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(RedisConstants.STREAM_ORDERS, ReadOffset.lastConsumed())
                    );
                    // 2. 如果获取的信息是空则说明没有消息，继续下一个循环
                    if (records == null || records.isEmpty()) {
                        continue;
                    }
                    // 3. 如果有消息，则解析消息内容，并且进行下单处理
                    MapRecord<String, Object, Object> record = records.getFirst();
                    handleScekillOrder(record);
                    // 4. 消息处理完成后，确认消息
                    // XACK stream.orders g1 messageId
                    stringRedisTemplate.opsForStream().acknowledge(RedisConstants.STREAM_ORDERS, STREAM_GROUP, record.getId());
                }catch (Exception e){
                    // 5. 如果消息处理过程中发生异常，则进入消息的异常处理循环
                    while (true) {
                        try {
                            // 6. 从Pending-list中获取未处理成功的消息
                            // XREADGROUP GROUP g1 c1 COUNT 1 STREAMS stream.orders 0
                            List<MapRecord<String, Object, Object>> records = stringRedisTemplate.opsForStream().read(
                                    Consumer.from(STREAM_GROUP, STREAM_CONSUMER),
                                    StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                                    StreamOffset.create(RedisConstants.STREAM_ORDERS, ReadOffset.from("0"))
                            );
                            // 7. 如果此时pending-list中没有异常消息，则跳出异常处理循环，继续正常的消息处理流程
                            if (records == null || records.isEmpty()) {
                                break;
                            }
                            // 8. 如果有异常，则再次尝试处理
                            MapRecord<String, Object, Object> record = records.getFirst();
                            handleScekillOrder(record);
                            // 9. 消息处理完成后，确认消息
                            // XACK stream.orders g1 messageId
                            stringRedisTemplate.opsForStream().acknowledge(RedisConstants.STREAM_ORDERS, STREAM_GROUP, record.getId());
                        }catch (Exception ex) {
                            // 10. 如果再次处理失败，则继续在当前的异常处理循环中进行处理
                            continue;
                        }
                    }
                }
            }
        }
    }
    // 5. 为了实现异步处理，使用@PostConstruct，在Bean初始化之后，启动一个异步线程，提交任务（实际的入库操作）
    @PostConstruct
    public void init() {
        // 提交异步处理的线程方法
        SECKILL_ORDER_EXECUTOR.submit(new SeckillOrderHandler());
    }

    // 6. 定义具体的入库方法
    @Transactional
    protected void handleScekillOrder(MapRecord<String, Object, Object> record) {
        // 1. 获取MapRecord中的健值对
        Map<Object, Object> orderInfo = record.getValue();
        // 2. 解析Map中的信息
        Long orderId = Long.valueOf(orderInfo.get("orderId").toString());
        Long userId = Long.valueOf(orderInfo.get("userId").toString());
        Long voucherId = Long.valueOf(orderInfo.get("voucherId").toString());
        // 3. 封装VoucherOrder对象
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);
        // 4. 实际扣减库存
        seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id",voucherId)
                .gt("stock",0) // 只有gt("stock",0)成立时才更新成功（stock > 0）
                .update();
        // 5. 实际插入订单数据
        save(voucherOrder);
    }

    @Override
    public Long seckillVoucher(Long voucherId) {
        // 构建各种KEYS[] 和 ARGV[]
        // 1. KEYS[1] -- 库存 key：seckill:stock:{voucherId}
        String stockKey = RedisConstants.SECKILL_STOCK_KEY + voucherId;
        // 2. KEYS[2] -- 订单 key：seckill:order:users:{voucherId}
        String orderUsersKey = RedisConstants.SECKILL_ORDER_USERS_KEY + voucherId;
        // 3. KEYS[3] -- 消息队列 key：stream.orders
        String streamKey = RedisConstants.STREAM_ORDERS;

        // 4. ARGV[1] -- 用户ID,注意，数据类型应该是字符串，因为Redis中存储的都是字符串
        String userId = UserHolder.getUser().getId().toString();
        // 5. ARGV[2] -- VoucherID
        String voucherIdStr = voucherId.toString();
        // 6. ARGV[3] -- 订单ID
        String orderId = String.valueOf(redisIdWorker.nextId("voucher_order"));

        // 执行lua脚本，并传递参数
        Long result = stringRedisTemplate.execute(SECKILL_SCRIPT,
                List.of(stockKey, orderUsersKey, streamKey),
                userId, voucherIdStr, orderId
        );
        // 解析结果
        if (result == 1L) {
            throw new RuntimeException("库存不足！");
        }
        else if (result == 2L) {
            throw new RuntimeException("每个用户只能购买一次！");
        }
        // 结果解析为0L, 表示消息队列中已经成功添加了订单信息

        // 返回订单ID
        return Long.valueOf(orderId);
    }
}
