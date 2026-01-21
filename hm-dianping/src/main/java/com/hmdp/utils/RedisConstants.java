package com.hmdp.utils;

public class RedisConstants {
    /**
     * 登录验证码key前缀
     */
    public static final String LOGIN_CODE_KEY = "login:code:";

    /**
     * 登录验证码有效期
     */
    public static final Long LOGIN_CODE_TTL = 5L;

    /**
     * 登录用户key前缀
     */
    public static final String LOGIN_USER_KEY = "login:token:";

    /**
     * 登录用户有效期
     */
    public static final Long LOGIN_USER_TTL = 60L;

    /**
     * 缓存空值时间
     */
    public static final Long CACHE_NULL_TTL = 2L;

    /**
     * 缓存shop有效期
     */
    public static final Long CACHE_SHOP_TTL = 30L;

    /**
     * 缓存shop key前缀
     */
    public static final String CACHE_SHOP_KEY = "cache:shop:";

    /**
     * 锁key前缀
     */
    public static final String LOCK_SHOP_KEY = "lock:shop:";

    /**
     * 锁有效期
     */
    public static final Long LOCK_SHOP_TTL = 10L;

    /**
     * 秒杀库存key前缀
     */
    public static final String SECKILL_STOCK_KEY = "seckill:stock:";

    /**
     * 博客点赞key前缀
     */
    public static final String BLOG_LIKED_KEY = "blog:liked:";

    /**
     * feed流key前缀
     */
    public static final String FEED_KEY = "feed:";

    public static final String SHOP_GEO_KEY = "shop:geo:";

    public static final String USER_SIGN_KEY = "sign:";
}
