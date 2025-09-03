package com.gopair.websocketservice.constants;

/**
 * WebSocket服务常量定义
 * 统一管理所有WebSocket相关的常量
 * 
 * @author gopair
 */
public class WebSocketConstants {
    
    // ==================== 核心限制常量 ====================
    
    /**
     * 单用户最大订阅频道数
     */
    public static final int MAX_SUBSCRIPTIONS_PER_USER = 50;
    
    /**
     * 单房间最大连接数
     */
    public static final int MAX_CONNECTIONS_PER_ROOM = 1000;
    
    /**
     * 每用户每秒最大消息发送数
     */
    public static final int MAX_MESSAGES_PER_SECOND = 5;
    
    // ==================== Redis过期时间常量 ====================
    
    /**
     * 订阅状态过期时间（小时）
     */
    public static final long SUBSCRIPTION_EXPIRE_HOURS = 24;
    
    /**
     * 权限缓存过期时间（小时）
     */
    public static final long PERMISSION_CACHE_EXPIRE_HOURS = 2;
    
    // ==================== 核心Redis键前缀 ====================
    
    /**
     * 用户订阅键前缀
     */
    public static final String USER_SUBSCRIPTIONS_PREFIX = "ws:user:subscriptions:";
    
    // 注释：消息类型、频道前缀、事件类型等常量已移除
    // 理由：这些常量使用频率低，直接在代码中使用字符串更简洁
    // 如果需要类型安全，建议使用枚举类型而不是字符串常量
    
    // ==================== 私有构造函数 ====================
    
    /**
     * 私有构造函数，防止实例化
     */
    private WebSocketConstants() {
        // 工具类不应被实例化
    }
} 