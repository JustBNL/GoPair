package com.gopair.chatservice.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 私聊消息类型枚举。
 *
 * @author gopair
 */
@Getter
@AllArgsConstructor
public enum PrivateMessageType {

    TEXT(1, "文本消息"),
    IMAGE(2, "图片消息"),
    FILE(3, "文件消息");

    private final int code;
    private final String description;

    public static PrivateMessageType fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (PrivateMessageType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        return null;
    }
}
