package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock{
    private static final String KEY_PREFIX = "lock:";
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
        long threadId = Thread.currentThread().getId();
        // 获取锁
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + lockName,
                String.valueOf(threadId),   // 锁的持有线程标识
                timeoutSec, TimeUnit.SECONDS);
        // 判断获取锁成功与否 / null,false -> false
        return Boolean.TRUE.equals(success);
    }

    /**
     * 释放锁
     */
    public void unlock() {
        stringRedisTemplate.delete(KEY_PREFIX + lockName);
    }
}
