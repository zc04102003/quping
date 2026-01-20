package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.SeckillVoucherMapper;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import lombok.NonNull;
import org.springframework.aop.framework.AopContext;
import org.springframework.aop.framework.AopProxy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Autowired
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private RedisIdWorker idWorker;
    @Autowired
    StringRedisTemplate stringRedisTemplate;

    /**
     * 秒杀优惠券
     * @param voucherId 优惠券id
     * @return 订单信息
     */
    @Override
    public Result seckillVoucher(Long voucherId) {
        // 1.查询秒杀优惠券
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
        //2.判断秒杀是否开始
        LocalDateTime time = LocalDateTime.now();
        if (seckillVoucher.getBeginTime().isAfter(time)){
            // 未开始
            return Result.fail("秒杀活动尚未开始!");
        }
        //3.判断秒杀是否已经结束
        if (seckillVoucher.getEndTime().isBefore(time)) {
            // 已结束
            return Result.fail("秒杀活动已结束!");
        }
        //4.判断库存是否充足
        if (seckillVoucher.getStock() < 1)
            return Result.fail("库存不足!");

        Long userId = UserHolder.getUser().getId();
        // 创建锁对象,范围是用户ID
        SimpleRedisLock lock = new SimpleRedisLock("order" + userId, stringRedisTemplate);
        // 获取锁
        boolean isLock = lock.tryLock(5);
        if (!isLock) {
            // 获取锁失败
            return Result.fail("不允许重复下单!");
        }
        try {
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        } finally {
            // 释放锁
            lock.unlock();
        }
    }

    /**
     * 创建订单
     * @param voucherId 优惠券id
     * @return 订单信息
     */
    @Override
    @Transactional
    public @NonNull Result createVoucherOrder(Long voucherId) {
        // 判断一人一单
        Long userId = UserHolder.getUser().getId(); // 获取用户id

            // 4.查询订单
            long count = lambdaQuery().eq(VoucherOrder::getUserId, userId)  // 根据用户ID和优惠卷ID获取用户订单数量
                    .eq(VoucherOrder::getVoucherId, voucherId)
                    .count();
            if (count > 0) {
                return Result.fail("当前用户购买已上限!");
            }

            //5.扣减库存 stock = stock - 1
            boolean success = seckillVoucherService.lambdaUpdate()
                    .setSql("stock = stock - 1")    // 自定义sql语句
                    .eq(SeckillVoucher::getVoucherId, voucherId)
                    .gt(SeckillVoucher::getStock, 0)    // 库存大于0时才更新
                    .update();
            if (!success) {
                // 扣减失败
                return Result.fail("库存不足!");
            }

            //6.创建订单
            VoucherOrder voucherOrder = new VoucherOrder();
            Long orderId = idWorker.nextId("order");    // 生成订单id
            voucherOrder.setId(orderId);    // 订单id
            voucherOrder.setUserId(userId); // 用户id
            voucherOrder.setVoucherId(voucherId); // 优惠券id

            // 保存订单到SQL
            this.save(voucherOrder);
            //7.返回订单id
            return Result.ok(orderId);
    }
}
