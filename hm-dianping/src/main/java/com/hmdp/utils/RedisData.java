package com.hmdp.utils;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Redis数据
 */
@Data
public class RedisData<T> {
    /**
     * 过期时间
     */
    private LocalDateTime expireTime;
    /**
     * 数据
     */
    private T data;
}
