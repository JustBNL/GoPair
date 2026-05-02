package com.gopair.chatservice.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 好友关系状态枚举。
 *
 * @author gopair
 */
@Getter
@AllArgsConstructor
public enum FriendStatus {

    PENDING('0', "待确认"),
    ACCEPTED('1', "已同意"),
    REJECTED('2', "已拒绝");

    private final char code;
    private final String description;

    public static FriendStatus fromCode(Character code) {
        if (code == null) {
            return null;
        }
        for (FriendStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        return null;
    }
}
