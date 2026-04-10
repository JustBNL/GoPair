package com.gopair.voiceservice.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 通话参与者连接状态枚举
 *
 * @author gopair
 */
@Getter
@RequiredArgsConstructor
public enum ConnectionStatus {

    CONNECTED(1, "已连接"),
    DISCONNECTED(2, "已断开");

    private final int code;
    private final String desc;

    public static ConnectionStatus fromCode(int code) {
        for (ConnectionStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("未知的连接状态码: " + code);
    }
}
