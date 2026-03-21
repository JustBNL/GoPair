package com.gopair.messageservice.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 消息类型枚举
 * 
 * @author gopair
 */
@Getter
@AllArgsConstructor
public enum MessageType {
    
    /**
     * 文本消息
     */
    TEXT(1, "文本消息"),
    
    /**
     * 图片消息
     */
    IMAGE(2, "图片消息"),
    
    /**
     * 文件消息
     */
    FILE(3, "文件消息"),
    
    /**
     * 语音消息
     */
    VOICE(4, "语音消息"),

    /**
     * Emoji 互动消息（持久化存库，前端触发漂浮动画）
     */
    EMOJI(5, "Emoji互动");

    /**
     * 类型值
     */
    private final Integer value;

    /**
     * 类型描述
     */
    private final String description;

    /**
     * 根据值获取枚举
     * 
     * @param value 类型值
     * @return 消息类型枚举
     */
    public static MessageType fromValue(Integer value) {
        if (value == null) {
            return null;
        }
        
        for (MessageType type : MessageType.values()) {
            if (type.getValue().equals(value)) {
                return type;
            }
        }
        
        throw new IllegalArgumentException("未知的消息类型: " + value);
    }
} 