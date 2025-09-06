package com.gopair.websocketservice.protocol;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * WebSocket消息类型枚举
 * 
 * @author gopair
 */
public enum MessageType {
    
    /**
     * 聊天消息
     */
    CHAT("chat"),
    
    /**
     * WebRTC信令消息
     */
    SIGNALING("signaling"),
    
    /**
     * 文件传输相关消息
     */
    FILE("file"),
    
    /**
     * 系统消息
     */
    SYSTEM("system"),
    
    /**
     * 心跳消息
     */
    HEARTBEAT("heartbeat"),
    
    /**
     * 认证消息
     */
    AUTH("auth"),
    
    /**
     * 错误消息
     */
    ERROR("error"),
    
    /**
     * 连接状态消息
     */
    CONNECTION("connection"),
    
    /**
     * 订阅频道请求
     */
    SUBSCRIBE("subscribe"),
    
    /**
     * 取消订阅频道请求
     */
    UNSUBSCRIBE("unsubscribe"),
    
    /**
     * 订阅操作响应
     */
    SUBSCRIBE_RESPONSE("subscribe_response"),
    
    /**
     * 订阅状态更新通知
     */
    SUBSCRIPTION_UPDATE("subscription_update"),
    
    /**
     * 全局通知消息
     */
    GLOBAL_NOTIFICATION("global_notification"),
    
    /**
     * 频道消息（房间内的各种事件）
     */
    CHANNEL_MESSAGE("channel_message");

    private final String value;

    MessageType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static MessageType fromValue(String value) {
        for (MessageType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown message type: " + value);
    }
} 