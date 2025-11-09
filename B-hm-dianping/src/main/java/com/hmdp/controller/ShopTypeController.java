package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.service.IShopTypeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import java.util.List;

/**
 * <p>
 * 前端控制器
 * </p>
 */
@RestController
@RequestMapping("/shop-type")
public class ShopTypeController {
    private final IShopTypeService shopTypeService;
    @Autowired
    public ShopTypeController(IShopTypeService shopTypeService) {
        this.shopTypeService = shopTypeService;
    }

    @GetMapping("list")
    public Result queryTypeList() {
        List<ShopType> typeList = shopTypeService.getShopTypeList();
        return Result.ok(typeList);
    }
}
