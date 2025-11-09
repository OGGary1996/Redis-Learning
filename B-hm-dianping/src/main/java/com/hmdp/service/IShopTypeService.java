package com.hmdp.service;

import com.hmdp.entity.ShopType;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * <p>
 *  服务类
 * </p>
 */
public interface IShopTypeService extends IService<ShopType> {
    /*
    * 查询商铺分类
    * 替换掉MyBatis-Plus中直接查询数据库的方法，转为使用Redis缓存
    * */
    List<ShopType> getShopTypeList();

}
