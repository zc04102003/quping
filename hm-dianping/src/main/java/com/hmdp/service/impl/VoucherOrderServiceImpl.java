package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Autowired
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private RedisIdWorker idWorker;
    @Autowired
    StringRedisTemplate stringRedisTemplate;
    @Autowired
    private RedissonClient redissonClient;
    /**
     * 秒杀优惠券服务代理
     */
    IVoucherOrderService proxy;
    /**
     * 秒杀优惠券lua脚本加载
     */
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>(); // 脚本对象
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua")); // 脚本路径
        SECKILL_SCRIPT.setResultType(Long.class);    // 返回值类型
    }

    /**
     * 订单队列
     */
    private BlockingQueue<VoucherOrder> ordersTask = new ArrayBlockingQueue<>(1024 * 1024);
    /**
     * 线程池
     */
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();
    @PostConstruct
    private void init(){
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }
    /**
     * 订单处理线程
     */
    private class VoucherOrderHandler implements Runnable{
        @Override
        public void run() {
        while (true){
            try {
                // 获取队列中的订单信息
                VoucherOrder voucherOrder = ordersTask.take();
                // 创建订单
                handleVoucherOrder(voucherOrder);
            } catch (Exception e) {
                log.error("处理订单异常", e);
            }
        }
        }
    }

    /**
     * 创建订单
     * @param voucherOrder 订单信息
     */
    private void handleVoucherOrder(VoucherOrder voucherOrder) {
        // 创建锁对象,范围是用户ID

        RLock lock = redissonClient.getLock("order" + voucherOrder.getUserId());

        // 获取锁
        boolean isLock = lock.tryLock();
        if (!isLock) {
            // 获取锁失败
            log.error("不允许重复下单!");
        }
        try {
            proxy.createVoucherOrder(voucherOrder);
        } finally {
            // 释放锁
            lock.unlock();
        }
    }

    /**
     * 秒杀优惠券
     * @param voucherId 优惠券id
     * @return 订单信息
     */
    @Override
    public Result seckillVoucher(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        // 执行lua脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT, // 脚本对象
                Collections.emptyList(),    // key参数,0
                voucherId.toString(), userId.toString()   // 参数
        );

        // 判断结果是否为0
        int r = result.intValue();  // 转换为int
        if (r != 0) {
            return Result.fail(r == 1 ? "库存不足!" : "不能多次下单!");
        }

        // 为0, 有购买资格
        Long orderId = idWorker.nextId("order");
        // 把下单信息保存到阻塞队列
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(orderId);    // 订单id
        voucherOrder.setUserId(userId); // 用户id
        voucherOrder.setVoucherId(voucherId); // 优惠券id

        ordersTask.add(voucherOrder);   // 添加到队列
        // 获取代理对象
        proxy = (VoucherOrderServiceImpl) AopContext.currentProxy();
        return Result.ok(orderId);
    }
//    @Override
//    public Result seckillVoucher(Long voucherId) {
//        // 1.查询秒杀优惠券
//        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
//        //2.判断秒杀是否开始
//        LocalDateTime time = LocalDateTime.now();
//        if (seckillVoucher.getBeginTime().isAfter(time)){
//            // 未开始
//            return Result.fail("秒杀活动尚未开始!");
//        }
//        //3.判断秒杀是否已经结束
//        if (seckillVoucher.getEndTime().isBefore(time)) {
//            // 已结束
//            return Result.fail("秒杀活动已结束!");
//        }
//        //4.判断库存是否充足
//        if (seckillVoucher.getStock() < 1)
//            return Result.fail("库存不足!");
//
//        Long userId = UserHolder.getUser().getId();
//        // 创建锁对象,范围是用户ID
//        //SimpleRedisLock lock = new SimpleRedisLock("order" + userId, stringRedisTemplate);
//        RLock lock = redissonClient.getLock("order" + userId);
//
//        // 获取锁
//        boolean isLock = lock.tryLock();
//        if (!isLock) {
//            // 获取锁失败
//            return Result.fail("不允许重复下单!");
//        }
//        try {
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//            return proxy.createVoucherOrder(voucherId);
//        } finally {
//            // 释放锁
//            lock.unlock();
//        }
//    }

    /**
     * 创建订单
     * @return 订单信息
     */
    @Override
    @Transactional
    public void createVoucherOrder(VoucherOrder voucher) {
        // 判断一人一单
        Long userId = voucher.getUserId();

        // 4.查询订单
            long count = lambdaQuery().eq(VoucherOrder::getUserId, userId)  // 根据用户ID和优惠卷ID获取用户订单数量
                    .eq(VoucherOrder::getVoucherId, voucher.getVoucherId())
                    .count();
            if (count > 0) {
                log.error("用户已经购买过了!");
            }

            //5.扣减库存 stock = stock - 1
            boolean success = seckillVoucherService.lambdaUpdate()
                    .setSql("stock = stock - 1")    // 自定义sql语句
                    .eq(SeckillVoucher::getVoucherId, voucher.getVoucherId()) // Changed from voucherId to voucher.getVoucherId()
                    .gt(SeckillVoucher::getStock, 0)    // 库存大于0时才更新
                    .update();
            if (!success) {
                // 扣减失败
                log.error("库存不足!");
            }

            // 保存订单到SQL
            this.save(voucher);
    }
}
