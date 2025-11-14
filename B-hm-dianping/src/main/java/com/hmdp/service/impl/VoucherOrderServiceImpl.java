package com.hmdp.service.impl;

import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import com.hmdp.utils.UserMutexManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    // private final UserMutexManager userMutexManager;
    // 优化为分布式锁, new SimpleRedisLock
    private final StringRedisTemplate stringRedisTemplate;
    private final SeckillVoucherServiceImpl seckillVoucherService;
    private final RedisIdWorker redisIdWorker;
    @Autowired
    public VoucherOrderServiceImpl(SeckillVoucherServiceImpl seckillVoucherService, RedisIdWorker redisIdWorker, StringRedisTemplate stringRedisTemplate) {
        this.seckillVoucherService = seckillVoucherService;
        this.redisIdWorker = redisIdWorker;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /*
    * 新增秒杀订单记录
    * */
    @Override
    @Transactional
    public void seckillVoucher(Long voucherId) {
        // 1. 获取秒杀券，来自于seckillVoucherService中
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
        // 2. 判断是否在秒杀时间内
        LocalDateTime beginTime = seckillVoucher.getBeginTime();
        LocalDateTime endTime = seckillVoucher.getEndTime();
        if (LocalDateTime.now().isBefore(beginTime)) { // 过早
            throw new RuntimeException("秒杀尚未开始！");
        }
        if (LocalDateTime.now().isAfter(endTime)) { // 过晚
            throw new RuntimeException("秒杀已经结束！");
        }
        // 3. 判断库存是否充足
        Integer stock = seckillVoucher.getStock();
        if (stock < 1) {
            throw new RuntimeException("库存不足！");
        }

        // 4. 一人一单判断，防止同一用户多次下单,保证幂等性
        // 判断一人一单 + 减库存 + 创建订单的操作需要放在同一个锁中
        // 用户Id, 从ThreadLocal中获取当前用户
        Long userId = UserHolder.getUser().getId();

        // 使用分布式锁的方式解决一人一单问题
        SimpleRedisLock redisLock = new SimpleRedisLock("name:" + userId, stringRedisTemplate);
        boolean isLock = redisLock.tryLock(20); // 尝试获取锁，1000秒超时
        if (!isLock){ // 获取锁失败，说明已经有一个线程在为该用户创建订单
            throw new RuntimeException("不允许重复下单！");
        }
        // 获取锁成功，进入下单流程
        try {
            // 4.1 统计当前用户的订单数量
            Long count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
            // 4.2 判断是否已经购买过
            if (count > 0) {
                throw new RuntimeException("每个用户只能购买一次！");
            }

            // 5. 时间判断 & 库存判断通过，扣减库存，创建订单
            // 通过MyBatis-Plus的update方法实现扣减库存
            boolean isSuccess = seckillVoucherService.update()
                    .setSql("stock = stock - 1")
                    .eq("voucher_id", voucherId)
                    .eq("stock", stock) // 乐观锁，将stock作为version版本号，确保stock没有被其他线程修改
                    .gt("stock", 0) // 只有gt("stock",0)成立时才更新成功（stock > 0）
                    .update();
            if (!isSuccess) {
                throw new RuntimeException("库存不足！");
            }
            // 6. 无重复购买，可以创建订单，插入到voucher_order表中
            VoucherOrder voucherOrder = new VoucherOrder();
            // 订单id，采用Redis生成全局唯一id
            long id = redisIdWorker.nextId("voucher_order");
            voucherOrder.setId(id);
            voucherOrder.setUserId(userId);
            voucherOrder.setVoucherId(voucherId);
            save(voucherOrder);
        }finally {
            // 释放锁
            redisLock.unlock();
        }
    }
}
