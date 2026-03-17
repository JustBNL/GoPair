package com.gopair.voiceservice.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 通话类型枚举
 *
 * @author gopair
 */
@Getter
@RequiredArgsConstructor
public enum CallType {

    ONE_TO_ONE(1, "一对一"),
    MULTI_USER(2, "多人");

    private final int code;
    private final String desc;

    public static CallType fromCode(int code) {
        for (CallType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("未知的通话类型码: " + code);
    }
}
