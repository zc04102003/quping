package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Slf4j
@Component
public class CacheClient {

    private StringRedisTemplate stringRedisTemplate;
    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 设置缓存
     * @param key 缓存的key
     * @param value 缓存的值
     * @param time 缓存的时间
     * @param unit 时间单位
     */
    public void set(String key, Object value, Long time, TimeUnit unit){
        stringRedisTemplate.opsForValue().set(key,
                JSONUtil.toJsonStr(value),
                time,
                unit
        );
    }

    /**
     * 缓存逻辑过期
     * @param key 缓存的key
     * @param value 缓存的值
     * @param time 缓存的时间
     * @param unit 时间单位
     * @param <T> 需要缓存的数据类型
     */
    public <T> void setWithLogicalExpire(String key, T value, Long time, TimeUnit unit){
        // 设置逻辑过期
        RedisData<T> redisData = new RedisData<>();
        redisData.setData(value);   // 缓存数据
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time))); // 逻辑过期时间

        // 写入Redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    /**
     * 查询缓存
     * @param keyPre 缓存的key前缀
     * @param id 缓存的key的后缀
     * @param type 缓存的数据类型
     * @return 缓存的数据
     * @param <R> 返回的数据
     * @param <T> id数据类型
     */
    public <R, T> R queryWithPassThrough(String keyPre, T id, Class<R> type,
                                         Long time,
                                         TimeUnit unit,
                                         Function<T, R> function){
        String key = keyPre + id;   // 缓存的key
        String s = stringRedisTemplate.opsForValue().get(key);
        // 判断缓存是否存在
        if (StrUtil.isNotBlank(s)) {
            // 存在则返回
            return JSONUtil.toBean(s, type);
        }
        if (s != null){
            // 是空值
            return null;
        }
        // 缓存不存在, 查询数据库
        R r = function.apply(id);
        if (r == null) {
            // 将空值写入Redis
            stringRedisTemplate.opsForValue().set(key, "", time, unit);
            return null;
        }
        // 写入Redis
        this.set(key, JSONUtil.toJsonStr(r), time, unit);

        return r;
    }

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
}
