package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10); // 线程池，用于缓存重建

    /**
     * 根据id查询商铺信息 : 逻辑过期
     * @param id 商铺id
     * @return 商铺详情数据
     */
    @Override
    public Result selectShopById(Long id) {
        // 从Redis中查询商铺缓存
        String shopKey = RedisConstants.CACHE_SHOP_KEY + id;    // 缓存key
        String shopCache = stringRedisTemplate.opsForValue().get(shopKey);
        // 判断是否存在
        if (StrUtil.isBlank(shopCache)) {    // ""/null -> false
            // 未命中Redis缓存,直接返回空数据
            return null;
        }
        // 从Redis缓存中获取数据
        RedisData redisData = JSONUtil.toBean(shopCache, RedisData.class);  // 先把获取到的Redis数据转成实体类
        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);  // 把JSON转换成实体类
        LocalDateTime expireTime = redisData.getExpireTime();   // 缓存过期时间
        // 命中Redis数据,判断缓存是否过期
        if (expireTime.isAfter(LocalDateTime.now())){   // 是否在当前时间后
            // 未过期,返回店铺数据
            return Result.ok(shop);
        }
        // 已过期,需要缓存重建
        boolean isLock = this.tryLock(RedisConstants.LOCK_SHOP_KEY + id);   // 尝试获取锁
        if (isLock) {
            // TODO 获取成功,开启独立线程实现缓存重建,需要双层检查
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    // 缓存重建
                    this.saveShopRedis(id, 20L);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }finally {
                    // 释放锁
                    this.unLock(RedisConstants.LOCK_SHOP_KEY + id);
                }
            });
        }

        // 返回
        return Result.ok(shop);
    }

    /*
     * 根据id查询商铺信息 : 互斥锁
     * @param id 商铺id
     * @return 商铺详情数据
     */
    /*public Result selectShopById(Long id) {
        // 从Redis中查询商铺缓存
        String shopKey = RedisConstants.CACHE_SHOP_KEY + id;    // 缓存key
        String shopCache = stringRedisTemplate.opsForValue().get(shopKey);
        // 判断是否存在
        if (StrUtil.isNotBlank(shopCache)) {    // ""/null -> false
            // 不为空,直接返回
            Shop shop = JSONUtil.toBean(shopCache, Shop.class); // 把JSON转换成实体类
            return Result.ok(shop);
        }

        // 判断Redis命中的是否是空值
        if (shopCache != null) {    // shopCache == ""
            return Result.fail("店铺不存在!");
        }

        Shop shop = null;

        try {
            // 获取互斥锁
            boolean isLock = this.tryLock(RedisConstants.LOCK_SHOP_KEY + id);
            // 判断获取结果
            if (!isLock) {
                // 失败,则休眠并重试(Redis重新查)
                Thread.sleep(50);
                return selectShopById(id);  // 递归查询
            }

            // Redis不存在,则查询数据库,再放入Redis中
            shop = this.getById(id);
            if (shop == null){
                // 将空值写入Redis,防止缓存穿透
                stringRedisTemplate.opsForValue().set(shopKey,
                        "",      // 空值
                        RedisConstants.CACHE_NULL_TTL,   // 空值有效期_2min
                        TimeUnit.MINUTES
                );
                // 返回错误信息
                return Result.fail("店铺不存在!");
            }

            shopCache = JSONUtil.toJsonStr(shop);    // 转成JSON
            stringRedisTemplate.opsForValue().set(shopKey, shopCache,
                    RedisConstants.CACHE_SHOP_TTL, // 设置缓存有效时间_30min
                    TimeUnit.MINUTES    // 时间单位_min
            );
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }finally {
            // 释放锁
            this.unLock(RedisConstants.LOCK_SHOP_KEY + id);
        }

        // 返回
        return Result.ok(shop);
    }*/

    /*
     * 根据id查询商铺信息 : 缓存穿透
     * @return 商铺详情数据
     */
    /*public Result selectShopById(Long id) {
        // 从Redis中查询商铺缓存
        String shopKey = RedisConstants.CACHE_SHOP_KEY + id;    // 缓存key
        String shopCache = stringRedisTemplate.opsForValue().get(shopKey);
        // 判断是否存在
        if (StrUtil.isNotBlank(shopCache)) {    // ""/null -> false
            // 不为空,直接返回
            Shop shop = JSONUtil.toBean(shopCache, Shop.class); // 把JSON转换成实体类
            return Result.ok(shop);
        }

        // 判断Redis命中的是否是空值
        if (shopCache != null) {    // shopCache == ""
            return Result.fail("店铺不存在!");
        }

        // Redis不存在,则先从SQL中取出,再放入Redis中
        Shop shop = this.getById(id);
        if (shop == null){
            // 将空值写入Redis,防止缓存穿透
            stringRedisTemplate.opsForValue().set(shopKey,
                    "",      // 空值
                    RedisConstants.CACHE_NULL_TTL,   // 空值有效期_2min
                    TimeUnit.MINUTES
            );
            // 返回错误信息
            return Result.fail("店铺不存在!");
        }

        shopCache = JSONUtil.toJsonStr(shop);    // 转成JSON
        stringRedisTemplate.opsForValue().set(shopKey, shopCache,
                RedisConstants.CACHE_SHOP_TTL, // 设置缓存有效时间_30min
                TimeUnit.MINUTES    // 时间单位_min
        );
        // 返回
        return Result.ok(shop);
    }
*/
    /**
     * 尝试获取锁
     * @return 获取锁成功返回true,获取锁失败返回false
     */
    private boolean tryLock(String key){
        // 尝试获取锁
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1",
                RedisConstants.LOCK_SHOP_TTL,   // 锁有效期_10s
                TimeUnit.SECONDS    // 时间单位_s
        );
        // 判断获取锁成功与否
        return BooleanUtil.isTrue(flag);
    }

    /**
     * 释放锁
     * @param key 锁的key
     */
    private void unLock(String key){
        // 释放锁
        stringRedisTemplate.delete(key);
    }

    /**
     * 缓存重建
     * @param id 商铺id
     * @param expireSeconds 过期秒数
     */
    public void saveShopRedis(Long id, Long expireSeconds){
        // 查询数据库
        Shop shop = this.getById(id);
        // 写入逻辑过期时间
        RedisData<Shop> shopRedisData = new RedisData<>();
        shopRedisData.setData(shop);
        // 缓存逻辑过期时间
        shopRedisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        // 写入Redis
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id,
                JSONUtil.toJsonStr(shopRedisData)   // 转成JSON
        );
    }

    /**
     * 更新商铺信息
     * @param shop 商铺数据
     * @return 无
     */
    @Override
    @Transactional      // 添加事务
    public Result updateShop(Shop shop) {
        if (shop.getId() == null) {
            return Result.fail("店铺ID不能为空!");
        }

        // 先更新数据库
        this.updateById(shop);
        // 删除Redis中的缓存
        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY+ shop.getId());

        return Result.ok();
    }
}
