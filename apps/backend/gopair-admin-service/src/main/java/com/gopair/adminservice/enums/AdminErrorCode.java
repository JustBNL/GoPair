package com.gopair.adminservice.enums;

import com.gopair.common.enums.ErrorCode;

/**
 * 管理员服务错误码枚举类
 *
 * 实现ErrorCode接口，定义管理员服务特有的错误码和错误信息。
 * 错误码规则：采用5位数字格式 A-BB-CCC
 * - A: 错误级别 (2=业务级错误)
 * - BB: 服务标识 (00=admin服务)
 * - CCC: 具体错误序号，从000开始连续递增
 *
 * 管理员服务错误码范围：20000-20099
 *
 * @author gopair
 */
public enum AdminErrorCode implements ErrorCode {

    ADMIN_NOT_FOUND(20000, "管理员账号不存在"),
    ADMIN_DISABLED(20001, "管理员账号已被停用"),
    ADMIN_PASSWORD_ERROR(20002, "密码错误"),

    USER_NOT_FOUND(20003, "用户不存在"),
    ROOM_NOT_FOUND(20004, "房间不存在"),
    FILE_NOT_FOUND(20005, "文件记录不存在"),
    VOICE_CALL_NOT_FOUND(20006, "通话记录不存在"),
    EMAIL_ALREADY_USED(20007, "该邮箱已被其他用户使用");

    private final int code;
    private final String message;

    AdminErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public int getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
