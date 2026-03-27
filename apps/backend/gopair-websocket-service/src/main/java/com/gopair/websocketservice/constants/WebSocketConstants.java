package com.gopair.websocketservice.constants;

public class WebSocketConstants {
    
    // ==================== 核心限制常量 ====================
    
    public static final int MAX_SUBSCRIPTIONS_PER_USER = 50;
    public static final int MAX_CONNECTIONS_PER_ROOM = 1000;
    public static final int MAX_MESSAGES_PER_SECOND = 5;

    // ==================== 令牌桶限流常量 ====================

    /** 令牌桶容量（最大允许突发消息数） */
    public static final int TOKEN_BUCKET_CAPACITY = 10;

    /** 令牌补充速率（个/秒） */
    public static final int TOKEN_BUCKET_REFILL_RATE = 5;

    /** 令牌桶 Redis Key 前缀 */
    public static final String TOKEN_BUCKET_KEY_PREFIX = "ws:rate:token_bucket:";

    /** 令牌桶 Redis Key TTL（秒），空闲超时后自动回收 */
    public static final long TOKEN_BUCKET_TTL_SECONDS = 600L;
    
    // ==================== Redis过期时间常量 ====================
    
    public static final long SUBSCRIPTION_EXPIRE_HOURS = 24;
    public static final long PERMISSION_CACHE_EXPIRE_HOURS = 2;
    
    // ==================== 核心Redis键前缀 ====================
    
    public static final String USER_SUBSCRIPTIONS_PREFIX = "ws:user:subscriptions:";
    
    // ==================== 频道前缀常量 ====================
    
    public static final String CHANNEL_PREFIX_USER = "user:";
    public static final String CHANNEL_PREFIX_ROOM = "room:";
    public static final String CHANNEL_PREFIX_SYSTEM = "system:";
    public static final String CHANNEL_PREFIX_VOICE = "voice:";
    
    // ==================== 私有构造函数 ====================
    
    private WebSocketConstants() {}
}
