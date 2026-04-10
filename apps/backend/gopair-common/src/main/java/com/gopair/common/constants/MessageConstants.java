package com.gopair.common.constants;

/**
 * 消息常量类
 * 
 * 统一管理系统中的提示信息常量，便于维护和国际化
 * 注意：错误信息已迁移到对应的ErrorCode枚举中，此处主要保留验证、通用操作等常量
 * 
 * @author gopair
 */
public class MessageConstants {

    /**
     * 通用消息
     */
    public static final String SUCCESS = "操作成功";
    public static final String FAILED = "操作失败";
    
    /**
     * 字段验证相关消息
     */
    public static final String NICKNAME_LENGTH_ERROR = "昵称长度必须在1-20个字符之间";
    public static final String PASSWORD_LENGTH_ERROR = "密码长度必须在6-50个字符之间";
    public static final String EMAIL_FORMAT_ERROR = "请输入有效的邮箱地址";
    public static final String PARAM_MISSING = "参数缺失";
    
    /**
     * 特殊业务场景消息
     */
    public static final String USER_PREFIX = "用户";

    /**
     * 请求头常量
     */
    public static final String HEADER_TRACE_ID = "X-Trace-Id";
    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_NICKNAME = "X-Nickname";
    
    /**
     * JWT相关常量
     */
    public static final String JWT_COOKIE_NAME = "token";
    public static final String AUTHORIZATION_HEADER = "Authorization";
    public static final String BEARER_PREFIX = "Bearer ";

    /**
     * WebSocket 消息相关常量
     */
    public static final String WEBSOCKET_EXCHANGE = "websocket.topic";
    public static final String CHANNEL_ROOM_PREFIX = "room:";
    public static final String CHANNEL_USER_PREFIX = "user:";
    public static final String ROUTING_KEY_CHAT_ROOM = "chat.room";
    public static final String ROUTING_KEY_SYSTEM_USER = "system.user";
    public static final String ROUTING_KEY_SYSTEM_ROOM = "system.room";
    public static final String ROUTING_KEY_SYSTEM_OFFLINE = "system.offline";
    public static final String ROUTING_KEY_SIGNALING_USER = "signaling.user";
    public static final String ROUTING_KEY_FILE_PROGRESS = "file.progress";

    /**
     * RabbitMQ 队列常量
     */
    public static final String QUEUE_WEBSOCKET_CHAT = "websocket.chat";
    public static final String QUEUE_WEBSOCKET_SIGNALING = "websocket.signaling";
    public static final String QUEUE_WEBSOCKET_FILE = "websocket.file";
    public static final String QUEUE_WEBSOCKET_SYSTEM = "websocket.system";
    public static final String QUEUE_USER_OFFLINE = "user.offline.queue";

    /**
     * MDC (Mapped Diagnostic Context) 键常量
     */
    public static final String MDC_TRACE_ID = "traceId";
    public static final String MDC_USER_ID = "userId";
    public static final String MDC_NICKNAME = "nickname";

    /**
     * 私有构造函数，防止实例化
     */
    private MessageConstants() {
        throw new IllegalStateException("常量类不允许实例化");
    }
}