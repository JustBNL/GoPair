package com.gopair.common.constants;

/**
 * 全局系统常量类
 *
 * 统一管理系统中的提示信息、业务常量、请求头、JWT、MDC、MQ 资源名及 DLX/DLQ 死信常量。
 * 注意：错误信息已迁移到对应的 ErrorCode 枚举中，此处主要保留验证、通用操作等常量。
 *
 * @author gopair
 */
public class SystemConstants {

    public static final String SUCCESS = "操作成功";
    public static final String FAILED = "操作失败";

    public static final String NICKNAME_LENGTH_ERROR = "昵称长度必须在1-20个字符之间";
    public static final String PASSWORD_LENGTH_ERROR = "密码长度必须在6-50个字符之间";
    public static final String EMAIL_FORMAT_ERROR = "请输入有效的邮箱地址";
    public static final String PARAM_MISSING = "参数缺失";

    public static final String USER_PREFIX = "用户";

    public static final String HEADER_TRACE_ID = "X-Trace-Id";
    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_NICKNAME = "X-Nickname";

    public static final String JWT_COOKIE_NAME = "token";
    public static final String AUTHORIZATION_HEADER = "Authorization";
    public static final String BEARER_PREFIX = "Bearer ";

    /*
     * WebSocket MQ 资源常量
     */
    public static final String WEBSOCKET_EXCHANGE = "websocket.topic";
    public static final String CHANNEL_ROOM_PREFIX = "room:";
    public static final String CHANNEL_USER_PREFIX = "user:";

    /*
     * WebSocket routing key 常量
     */
    public static final String ROUTING_KEY_CHAT_ROOM = "chat.room";
    public static final String ROUTING_KEY_SYSTEM_USER = "system.user";
    public static final String ROUTING_KEY_SYSTEM_ROOM = "system.room";
    public static final String ROUTING_KEY_SYSTEM_OFFLINE = "system.offline";
    public static final String ROUTING_KEY_SIGNALING_USER = "signaling.user";
    public static final String ROUTING_KEY_FILE_PROGRESS = "file.progress";

    /*
     * Room 服务专用
     */
    public static final String EXCHANGE_ROOM_JOIN = "room.join.exchange";
    public static final String ROUTING_KEY_ROOM_JOIN = "room.join.requested";
    public static final String QUEUE_ROOM_JOIN = "room.join.queue";

    public static final String EXCHANGE_ROOM_LEAVE = "room.leave.exchange";
    public static final String ROUTING_KEY_ROOM_LEAVE = "room.leave.requested";
    public static final String QUEUE_ROOM_LEAVE = "room.leave.queue";

    /*
     * Voice 服务专用
     */
    public static final String QUEUE_VOICE_ROOM_CREATED = "voice.room.created.queue";

    /*
     * MDC 键常量
     */
    public static final String MDC_TRACE_ID = "traceId";
    public static final String MDC_USER_ID = "userId";
    public static final String MDC_NICKNAME = "nickname";

    /*
     * 私有构造函数，防止实例化
     */
    private SystemConstants() {
        throw new IllegalStateException("常量类不允许实例化");
    }

    /*
     * 死信（DLX/DLQ）全局常量
     * 全项目共享同一套 DLX/DLQ，所有服务复用 dl.exchange / dl.queue。
     * DL routing key 格式统一为 dl.<service>.<resource>。
     */

    /** 全项目统一的死信交换机 */
    public static final String DL_EXCHANGE = "dl.exchange";

    /** 全项目统一的死信队列 */
    public static final String DL_QUEUE = "dl.queue";

    /** DLQ Binding 通配符（匹配所有 dl.* 路由键） */
    public static final String DL_ROUTING_KEY_ALL = "dl.#";

    // WebSocket 服务死信路由键
    public static final String ROUTING_KEY_DL_WS_CHAT = "dl.ws.chat";
    public static final String ROUTING_KEY_DL_WS_SIGNALING = "dl.ws.signaling";
    public static final String ROUTING_KEY_DL_WS_FILE = "dl.ws.file";
    public static final String ROUTING_KEY_DL_WS_SYSTEM = "dl.ws.system";
    public static final String ROUTING_KEY_DL_WS_OFFLINE = "dl.ws.offline";

    // Room 服务死信路由键
    public static final String ROUTING_KEY_DL_ROOM_JOIN = "dl.room.join";

    /*
     * WebSocket 队列名常量
     */
    public static final String QUEUE_WEBSOCKET_CHAT = "websocket.chat";
    public static final String QUEUE_WEBSOCKET_SIGNALING = "websocket.signaling";
    public static final String QUEUE_WEBSOCKET_FILE = "websocket.file";
    public static final String QUEUE_WEBSOCKET_SYSTEM = "websocket.system";
    public static final String QUEUE_USER_OFFLINE = "user.offline.queue";
}
