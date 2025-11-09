package com.hmdp.service.impl;

import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 */
@Service
@Slf4j
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    private final StringRedisTemplate stringRedisTemplate;
    private final CacheClient cacheClient;
    @Autowired
    public ShopServiceImpl(StringRedisTemplate stringRedisTemplate, CacheClient cacheClient) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.cacheClient = cacheClient;
    }

    /*
    * 根据id查询商铺信息
    * 替换掉MyBatis-Plus中直接查询数据库的方法，转为使用Redis缓存 - 解决缓存穿透问题
    * 流程：
    *  1. 首先查询Redis缓存
    *  2. 如果缓存命中，直接返回商铺信息
    *   2.1 再次判断命中的是否为防止 「缓存穿透」 而存储的空值，如果是则抛出异常
    *  3. 如果缓存未命中，原始逻辑为查询数据库之后直接返回，现在优化为先将数据库查询结果写入缓存，防止 「缓存穿透」
    *  4. 如果数据库中不存在该商铺，原始逻辑为直接抛异常，现在优化为返回空值，防止 「缓存穿透」
    *  5. 如果数据库中存在该商铺，将商铺信息写入Redis缓存
    * */
//    @Override
//    public Shop queryById(Long id) {
//        // 使用CacheClient封装的方法查询缓存
//        return cacheClient.queryWithPassThrough(RedisConstants.CACHE_SHOP_KEY, id, Shop.class, this::getById, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
//    }

    /*
    * 根据id查询商铺信息
    * 替换掉MyBatis-Plus中直接查询数据库的方法，转为使用Redis缓存 - 解决缓存击穿问题
    * 流程：
    *  1. 首先查询Redis缓存
    *  2. 注意：此处不需要判断是否命中了缓存，因为使用了逻辑过期时间，肯定会查询到缓存，只是可能逻辑过期了
    *  3. 判断缓存是否过期：
    *   3.1 如果未过期，则直接返回商铺信息
    *   3.2 如果已经过期，则需要进行缓存重建
    *  4. 原逻辑为直接查询数据库，现在优化为通过互斥锁的方式防止 「缓存击穿」
    *  5. 尝试获取互斥锁，如果获取到锁，则开启独立线程进行缓存重建
    *  6. 如果未获取到锁，则直接返回过期的商铺信息
    * */
    @Override
    public Shop queryById(Long id) {
        // 使用CacheClient封装的方法查询缓存
        return cacheClient.queryWithLogicalExpire(RedisConstants.CACHE_SHOP_KEY, id, Shop.class, this::getById, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
    }


    /*
    * 更新商铺信息
    * 替换掉MyBatis-Plus中直接更新数据库的方法，增加删除缓存的逻辑
    * 流程：
    *  1. 先操作数据库，更新商铺信息
    *  2. 删除Redis缓存
    * */
    @Transactional
    @Override
    public void updateShop(Shop shop) {
        log.info("更新商铺信息，商铺id：{}", shop.getId());
        if (shop.getId() == null) {
            throw new RuntimeException("商铺id不能为空");
        }
        // 1. 先操作数据库，更新商铺信息
        updateById(shop);
        // 2. 删除Redis缓存
        String key = RedisConstants.CACHE_SHOP_KEY + shop.getId();
        log.debug("删除缓存，key：{}", key);
        stringRedisTemplate.delete(key);
    }
}
