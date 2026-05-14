package com.gopair.common.constants;

/**
 * 正则表达式常量类
 *
 * 统一管理前后端共用的校验正则，避免散落在各处难以维护。
 */
public class RegexConstants {

    /**
     * RFC 5322 简化版邮箱正则。
     * 覆盖常见邮箱格式，排除尾部点、连续特殊字符等常见错误。
     */
    public static final String EMAIL_PATTERN =
            "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$";

    /**
     * 邮箱格式错误提示（供 DTO 注解使用）
     */
    public static final String EMAIL_FORMAT_ERROR = "请输入有效的邮箱地址";

    private RegexConstants() {
        throw new IllegalStateException("常量类不允许实例化");
    }
}
