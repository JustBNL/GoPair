package com.gopair.userservice.enums;

import lombok.Getter;

/**
 * 验证码场景类型枚举
 *
 * 统一管理验证码 type 参数，避免字符串硬编码散落多处。
 *
 * @author gopair
 */
@Getter
public enum VerificationType {

    REGISTER("register", "注册"),
    RESET_PASSWORD("resetPassword", "重置密码");

    private final String code;
    private final String description;

    VerificationType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据 code 查找枚举
     */
    public static VerificationType fromCode(String code) {
        for (VerificationType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("未知的验证码类型: " + code);
    }
}
