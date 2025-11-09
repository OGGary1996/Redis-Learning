package com.hmdp.service.impl;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 */
@Service
@Slf4j
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    private final StringRedisTemplate stringRedisTemplate;
    @Autowired
    public ShopTypeServiceImpl(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /*
    * 查询商铺分类
    * 替换掉MyBatis-Plus中直接查询数据库的方法，转为使用Redis缓存
    * 流程：
    *  1. 首先查询Redis缓存
    *  2. 如果缓存命中，直接返回商铺分类列表
    *  3. 如果缓存未命中，查询数据库
    *  4. 如果数据库中不存在商铺分类，返回空列表
    *  5. 如果数据库中存在商铺分类，将商铺分类列表写入Redis缓存，并返回商铺分类列表
    * 注意：
    *  1. 使用Redis中的List数据类型存储商铺分类列表
    *  2. 获取到的缓存是由JSON字符串组成的List，需要将其转换为ShopType对象列表
    *  3. 同样在写入缓存时，也需要将ShopType对象转换为JSON字符串，组成List存入Redis
    * */
    @Override
    public List<ShopType> getShopTypeList() {
        // 查询缓存
        String key = RedisConstants.CACHE_SHOP_TYPE_KEY;
        List<String> typeListJSON = stringRedisTemplate.opsForList().range(key, 0, -1);
        if (typeListJSON != null && !typeListJSON.isEmpty()) {
            // 命中缓存，直接返回
            log.debug("缓存命中，商铺分类列表{}", typeListJSON);
            // 将其中的JSON字符串转换为ShopType对象并返回
            return typeListJSON.stream()
                    .map( typeJSON ->  JSONUtil.toBean(typeJSON, ShopType.class))
                    .toList();
        }
        // 缓存未命中，查询数据库
        log.debug("缓存未命中，查询数据库，商铺分类列表");
        List<ShopType> typeList = query().orderByAsc("sort").list();
        // 存入缓存
        if (typeList != null && !typeList.isEmpty()) {
            // 将ShopType对象转换为JSON字符串
            typeListJSON = typeList.stream()
                    .map(type -> JSONUtil.toJsonStr(type))
                    .toList();
            // 写入Redis缓存.value为List类型
            stringRedisTemplate.opsForList().rightPushAll(key, typeListJSON);
            // 设置缓存过期时间
            // 加入随机TTL
            long randomTTL = RedisConstants.CACHE_SHOP_TYPE_TTL + RandomUtil.randomLong(1,6); // 1~5分钟的随机值
            stringRedisTemplate.expire(key, randomTTL, TimeUnit.MINUTES);
            // 返回商铺分类列表
            return typeList;
        }
        // 数据库中不存在商铺分类，返回空列表
        return typeList;
    }
}
