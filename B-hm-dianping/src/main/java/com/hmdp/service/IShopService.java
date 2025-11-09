package com.hmdp.service;

import com.hmdp.entity.Shop;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 */
public interface IShopService extends IService<Shop> {

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
}
