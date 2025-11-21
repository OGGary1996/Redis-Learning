package com.hmdp.service;

import com.hmdp.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 */
public interface IVoucherOrderService extends IService<VoucherOrder> {

    /*
    * 新增秒杀订单记录
    * */
    Long seckillVoucher(Long voucherId);
}
