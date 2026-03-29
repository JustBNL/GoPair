package com.gopair.userservice.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 用户账号状态枚举
 *
 * 对应数据库 user.status 字段（Character 类型）
 *
 * @author gopair
 */
@Getter
@RequiredArgsConstructor
public enum UserStatus {

    /**
     * 正常
     */
    NORMAL('0', "正常"),

    /**
     * 停用
     */
    DISABLED('1', "停用"),

    /**
     * 已注销
     */
    CANCELLED('2', "已注销");

    /**
     * 注销邮箱软删除标记前缀，格式：原邮箱 + DELETED_EMAIL_SUFFIX + 时间戳
     */
    public static final String DELETED_EMAIL_SUFFIX = "#deleted_";

    private final Character code;
    private final String description;
}
