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

    // 提供对外输出的简化别名，兼容前端现有读取逻辑
    @com.fasterxml.jackson.annotation.JsonGetter("id")
    public String getJsonId() {
        return this.messageId;
    }

    @com.fasterxml.jackson.annotation.JsonGetter("data")
    public java.util.Map<String, Object> getJsonData() {
        return this.payload != null ? this.payload : java.util.Map.of();
    }
} 