package com.gopair.websocketservice.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.Map;

/**
 * 前端WebSocket消息DTO
 * 仅用于历史测试/工具路径；生产解析已统一采用 Map -> UnifiedWebSocketMessage。
 * 后续稳定版本可考虑移除本类，避免双路径维护成本。
 * 
 * @author gopair
 */
@Deprecated
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ClientWebSocketMessage {

    /**
     * 消息唯一ID
     */
    private String messageId;

    /**
     * 消息类型（字符串格式）
     */
    private String type;

    /**
     * 消息时间戳（ISO字符串格式）
     */
    private String timestamp;

    /**
     * 事件类型
     */
    private String eventType;

    /**
     * 频道名称
     */
    private String channel;

    /**
     * 消息载荷
     */
    private Map<String, Object> payload;

    /**
     * 额外的数据字段（前端可能发送）
     */
    private Map<String, Object> data;

    /**
     * 客户端操作类型
     */
    private String action;

    /**
     * 转换为UnifiedWebSocketMessage
     */
    @SuppressWarnings("unchecked")
    public UnifiedWebSocketMessage toUnifiedMessage() {
        UnifiedWebSocketMessage message = new UnifiedWebSocketMessage();
        message.setMessageId(this.messageId);
        message.setChannel(this.channel);
        message.setEventType(this.eventType);
        message.setAction(this.action);
        
        // 智能处理payload数据
        Map<String, Object> finalPayload = null;
        
        if (this.payload != null) {
            finalPayload = this.payload;
        } else if (this.data != null) {
            // 检查data中是否有嵌套的payload
            if (this.data.containsKey("payload") && this.data.get("payload") instanceof Map) {
                finalPayload = (Map<String, Object>) this.data.get("payload");
            } else {
                finalPayload = this.data;
            }
        }
        
        message.setPayload(finalPayload);
        
        // 处理时间戳
        if (this.timestamp != null) {
            try {
                message.setTimestamp(java.time.LocalDateTime.parse(this.timestamp, 
                    java.time.format.DateTimeFormatter.ISO_DATE_TIME));
            } catch (Exception e) {
                message.setTimestamp(java.time.LocalDateTime.now());
            }
        } else {
            message.setTimestamp(java.time.LocalDateTime.now());
        }
        
        // 转换消息类型
        try {
            message.setType(MessageType.fromValue(this.type));
        } catch (IllegalArgumentException e) {
            // 处理前端可能发送的类型值
            switch (this.type) {
                case "subscribe":
                    message.setType(MessageType.SUBSCRIBE);
                    break;
                case "heartbeat":
                    message.setType(MessageType.HEARTBEAT);
                    break;
                case "room_message":
                    message.setType(MessageType.CHAT);
                    break;
                default:
                    message.setType(MessageType.CHAT);
            }
        }
        
        return message;
    }
} 