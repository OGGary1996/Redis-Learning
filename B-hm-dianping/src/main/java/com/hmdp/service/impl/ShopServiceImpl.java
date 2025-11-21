package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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
    * 预热GEO信息
    * 注意：在实际项目中，这个方法不应该放在ServiceImpl中，而是应该放在一个专门的初始化类中
    * 该方法仅用于演示如何将商铺坐标信息存入Redis GEO
    *
    * 流程：
    *  1. 查询所有的店铺信息
    *  2. 将店铺分组，根据店铺类型进行分组，存入不同的Redis GEO key中
    *  3. 遍历分组后的店铺列表，写入Redis
    * */
    @PostConstruct
    public void initShopGeo() {
        // 1. 查询所有的店铺信息
        List<Shop> allShops = list();
        // 2. 将店铺分组，利用Collectors.groupingBy进行分组,key为店铺类型id，value为同类型店铺的集合
        Map<Long, List<Shop>> shopTypeGroup = allShops.stream().collect(Collectors.groupingBy(shop -> shop.getTypeId()));
        // 3. 遍历分组后的店铺列表，写入Redis
          // 3.1 获取所有的entrySet
        Set<Map.Entry<Long, List<Shop>>> entries = shopTypeGroup.entrySet();
        entries.forEach(entry -> {
            // 3.2 获取店铺的typeId,组成key
            Long typeId = entry.getKey();
            String key = RedisConstants.SHOP_GEO_KEY + typeId;
            // 3.3 获取同类型的店铺列表
            List<Shop> shops = entry.getValue();
            // 3.4 写入Redis GEO,注意for循环的效率不高，可以使用GeoLocation(内部就是memberName+Point)批量写入
//            shops.forEach(shop -> {
//                // GEOADD key 经度 纬度 成员
//                stringRedisTemplate.opsForGeo().add(key,new Point(shop.getX(), shop.getY()), shop.getId().toString());
//            });
            List<RedisGeoCommands.GeoLocation<String>> geoLocations = shops.stream().map(shop -> {
                // 将Shop转换为GeoLocation对象
                return new RedisGeoCommands.GeoLocation<>(shop.getId().toString(), new Point(shop.getX(), shop.getY()));
            }).toList();
            // 3.5 批量写入
            stringRedisTemplate.opsForGeo().add(key, geoLocations);
        });
    }


    /*
    * 保存商户同时将坐标信息存入Redis GEO
    * */
    @Override
    public void saveShop(Shop shop) {
        boolean isSaved = save(shop);
        if (isSaved) {
            // 保存成功，写入Redis GEO, GEOADD key 经度 纬度 成员
            String key = RedisConstants.SHOP_GEO_KEY + shop.getTypeId();
            stringRedisTemplate.opsForGeo().add(
                    key,
                    new Point(shop.getX(), shop.getY()),
                    shop.getId().toString()
            );
        }
    }

    /*
    * 更新商铺信息
    * 替换掉MyBatis-Plus中直接更新数据库的方法，增加删除缓存的逻辑
    * 流程：
    *  1. 先操作数据库，更新商铺信息
    *  2. 删除Redis缓存
    *
    * 优化：修改商铺信息时，同时更新Redis GEO中的坐标信息
    * */
    @Transactional
    @Override
    public void updateShop(Shop shop) {
        log.info("更新商铺信息，商铺id：{}", shop.getId());
        if (shop.getId() == null) {
            throw new RuntimeException("商铺id不能为空");
        }
        // 1. 先操作数据库，更新商铺信息
        boolean isUpdated = updateById(shop);
        // 2. 删除缓存 + 更新GEO
        if (isUpdated){
            // 删除缓存
            String key = RedisConstants.CACHE_SHOP_KEY + shop.getId();
            log.debug("删除缓存，key：{}", key);
            stringRedisTemplate.delete(key);
            // 更新GEO
            String geoKey = RedisConstants.SHOP_GEO_KEY + shop.getTypeId();
            stringRedisTemplate.opsForZSet().remove(geoKey, shop.getId().toString());
            stringRedisTemplate.opsForGeo().add(
                    geoKey,
                    new Point(shop.getX(), shop.getY()),
                    shop.getId().toString()
            );
        }
    }

    /*
    * 根据商铺类型分页查询商铺信息， 可选根据坐标查询附近商铺
    * 流程：
    *  1. 判断是否需要根据坐标查询，如果不需要，则直接按照普通条件分页查询即可
    *  2. 计算分页参数
    *  3. 查询Redis GEO，按照距离排序，并且分页。得到结果：店铺id + 距离
    *  4. 根据店铺id查询数据库，得到店铺信息
    *
    * */
    @Override
    public List<Shop> queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        // 如果需要坐标查询
        // 2. 计算Redis需要的分页参数
        int from = (current - 1) * SystemConstants.MAX_PAGE_SIZE;
        int end = current * SystemConstants.MAX_PAGE_SIZE;
        // 3. 查询Redis GEO，按照距离排序，并且分页。GEOSEARCH key BYLONLAT x y BYRADIUS 5000 m WITHDISTANCE
        String key = RedisConstants.SHOP_GEO_KEY + typeId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> geoResults = stringRedisTemplate.opsForGeo().search(
                key,
                GeoReference.fromCoordinate(new Point(x, y)), // FROMLONLAT x y
                new Distance(5000, RedisGeoCommands.DistanceUnit.METERS), // BYRADIUS 5000 m
                RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end) // 注意：GEOSEARCH没有from参数，所以只能先查到end，然后在Java代码中截取分页
        );
        if (geoResults == null) {
            // 没有结果，直接返回空页
            return List.of();
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> content = geoResults.getContent();
        // 4.1 截取分页结果，直接从from开始跳过
        List<Long> shopIds = new ArrayList<>(end-from);
        Map<Long, Distance> distanceMap = new HashMap<>(end-from);
        content.stream().skip(from).forEach(result -> {
            // 4.2 获取店铺id
            Long shopId = Long.valueOf(result.getContent().getName());
            shopIds.add(Long.valueOf(shopId));
            // 4.3 获取距离
            Distance distance = result.getDistance();
            distanceMap.put(shopId, distance);
        });

        // 5. 根据店铺id查询数据库，得到店铺信息
        // 注意，MyBatis-Plus的listByIds方法无法保证顺序，需要自己排序
        List<Shop> shops = listByIds(shopIds);
        Map<Long, Shop> shopMap = shops.stream().collect(Collectors.toMap(Shop::getId, shop -> shop));
        // 将店铺按照shopIds的顺序排序，并且需要给每个Shop设置距离distance字段，注意这个字段不属于数据库字段，是我们自己在Shop实体类中添加的
        return shopIds.stream().map(shopId -> {
            // 从map中获取Shop对象
            Shop shop = shopMap.get(shopId);
            // 设置距离，注意需要getValue()转换为double类型
            shop.setDistance(distanceMap.get(shopId).getValue());
            return shop;
        }).toList();
    }
}
