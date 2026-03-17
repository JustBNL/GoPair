package com.gopair.websocketservice.constants;

public class WebSocketConstants {
    
    // ==================== 核心限制常量 ====================
    
    public static final int MAX_SUBSCRIPTIONS_PER_USER = 50;
    public static final int MAX_CONNECTIONS_PER_ROOM = 1000;
    public static final int MAX_MESSAGES_PER_SECOND = 5;
    
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
