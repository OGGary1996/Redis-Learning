package com.hmdp;

import com.hmdp.service.IShopService;
import com.hmdp.service.impl.ShopServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class PreHeatCache {
    private final ShopServiceImpl shopServiceImpl;
    @Autowired
    public PreHeatCache(ShopServiceImpl shopServiceImpl) {
        this.shopServiceImpl = shopServiceImpl;
    }

    @Test
    public void preHeatShopCache() {
        shopServiceImpl.saveShopToCache(1L,10L);
    }
}
