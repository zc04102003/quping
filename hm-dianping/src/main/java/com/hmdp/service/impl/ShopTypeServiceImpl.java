package com.hmdp.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 查询所有商铺类型
     * @return 商铺类型列表
     */
    @Override
    public Result selectShopTypeList() {
        // 先查询Redis中是否有数据
        String key = "cache:shopType:list";
//        String cacheShopTypeList = stringRedisTemplate.opsForValue().get(key);

        List<String> stringList = stringRedisTemplate.opsForList().range(key, 0, -1);
        if (stringList != null && CollectionUtil.isNotEmpty(stringList)){
            List<ShopType> list = new ArrayList<>();
            for (String s : stringList) {
                list.add(JSONUtil.toBean(s, ShopType.class));   // 把JSON转换成实体类,再加入到list中
            }
            return Result.ok(list);
        }

//        if (StrUtil.isNotBlank(cacheShopTypeList)){
//            // 存在,直接返回
//            List<ShopType> shopTypeList = JSONUtil.toList(cacheShopTypeList, ShopType.class);
//            return Result.ok(shopTypeList);
//        }

        // 不存在,先查询数据库是否存在
        List<ShopType> shopTypeList = lambdaQuery().orderByAsc(ShopType::getSort).list();
        if (shopTypeList == null || shopTypeList.isEmpty())
            return Result.fail("暂无店铺类型...");

        // SQL存在,写入Redis
//        cacheShopTypeList = JSONUtil.toJsonStr(shopTypeList);
//        stringRedisTemplate.opsForValue().set(key, cacheShopTypeList);

        for (ShopType shopType : shopTypeList) {
            // 把一个一个对象添加到Redis的集合中
            stringRedisTemplate.opsForList().rightPush(key, JSONUtil.toJsonStr(shopType));
        }

        // 返回
        return Result.ok(shopTypeList);
    }
}
