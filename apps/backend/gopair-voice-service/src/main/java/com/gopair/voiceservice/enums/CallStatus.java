package com.gopair.voiceservice.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 通话状态枚举
 *
 * @author gopair
 */
@Getter
@RequiredArgsConstructor
public enum CallStatus {

    IN_PROGRESS(1, "进行中"),
    ENDED(2, "已结束"),
    CANCELLED(3, "已取消");

    private final int code;
    private final String desc;

    public static CallStatus fromCode(int code) {
        for (CallStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("未知的通话状态码: " + code);
    }
}
