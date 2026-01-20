package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock{
    /**
     * 锁名称前缀
     */
    private static final String KEY_PREFIX = "lock:";
    /**
     * 线程标识前缀
     */
    private static final String ID_PREFIX = UUID.randomUUID().toString(true)+ "-";
    /**
     * 释放锁的lua脚本
     */
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>(); // 脚本对象
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua")); // 脚本路径
        UNLOCK_SCRIPT.setResultType(Long.class);    // 返回值类型
    }

    /**
     * 锁名称
     */
    private String lockName;
    private StringRedisTemplate stringRedisTemplate;

    public SimpleRedisLock(String lockName, StringRedisTemplate stringRedisTemplate) {
        this.lockName = lockName;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 尝试获取锁
     * @param timeoutSec 锁的过期时间,单位_秒
     * @return true -> 获取锁成功 / false -> 获取锁失败
     */
    @Override
    public boolean tryLock(long timeoutSec) {
        // 获取线程标识
//        long threadId = Thread.currentThread().getId();
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        // 获取锁
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + lockName,
                threadId,   // 锁的持有线程标识
                timeoutSec, TimeUnit.SECONDS);
        // 判断获取锁成功与否 / null,false -> false
        return Boolean.TRUE.equals(success);
    }

    /**
     * 释放锁
     */
    @Override
    public void unlock() {
        // 调用lua脚本
        stringRedisTemplate.execute(
                UNLOCK_SCRIPT,
                Collections.singletonList(KEY_PREFIX + lockName),   // 锁的key
                ID_PREFIX + Thread.currentThread().getId()
        );

        stringRedisTemplate.delete(KEY_PREFIX + lockName);
    }
//    @Override
//    public void unlock() {
//        // 获取线程标识
//        String threadId = ID_PREFIX + Thread.currentThread().getId();
//        // 获取锁中的标识
//        String id = stringRedisTemplate.opsForValue().get(KEY_PREFIX + lockName);
//
//        // 判断标识是否一致
//        if (threadId.equals(id)) {
//            // 释放
//            stringRedisTemplate.delete(KEY_PREFIX + lockName);
//        }
//    }
}
