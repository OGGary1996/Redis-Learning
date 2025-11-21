package com.hmdp.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * <p>
 *  服务类
 * </p>
 */
public interface IShopService extends IService<Shop> {

    /*
    *
    * */
    void saveShop(Shop shop);

    /*
    * 根据id查询商铺信息
    * 替换掉MyBatis-Plus中直接查询数据库的方法，转为使用Redis缓存
    * */
    Shop queryById(Long id);

    /*
    * 更新商铺信息
    * 替换掉MyBatis-Plus中直接更新数据库的方法，增加删除缓存的逻辑
    * */
    void updateShop(Shop shop);

    /*
    * 根据商铺类型分页查询商铺信息， 可选根据坐标查询附近商铺
    * */
    List<Shop> queryShopByType(Integer typeId, Integer current, Double x, Double y);
}
