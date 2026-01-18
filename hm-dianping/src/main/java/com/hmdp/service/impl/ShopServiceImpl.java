package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    /**
     * 根据id查询商铺信息
     * @param id 商铺id
     * @return 商铺详情数据
     */
    @Override
    public Result selectShopById(Long id) {
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
