package com.gopair.websocketservice.protocol;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 统一WebSocket消息协议
 * 
 * @author gopair
 */
@Data
@Accessors(chain = true)
public class UnifiedWebSocketMessage {

    /**
     * 消息唯一ID
     */
    private String messageId;

    /**
     * 消息时间戳
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime timestamp;

    /**
     * 消息类型
     */
    private MessageType type;



    /**
     * 频道名称 (如: "room:chat:123", "user:456")
     */
    private String channel;

    /**
     * 事件类型 (如: "message", "typing", "join", "leave")
     */
    private String eventType;

    /**
     * 客户端操作类型 (如: "subscribe", "unsubscribe", "publish")
     */
    private String action;

    /**
     * 消息载荷
     */
    private Map<String, Object> payload;

    /**
     * 消息来源服务
     */
    private String source;

    /**
     * 路由键（用于RabbitMQ）
     */
    private String routingKey;

    /**
     * 消息优先级（0-255，数字越大优先级越高）
     */
    private Integer priority;

    /**
     * 是否需要确认收到
     */
    private Boolean requireAck;

    /**
     * 消息超时时间（毫秒）
     */
    private Long ttl;
} 