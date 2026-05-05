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
    ADMIN_PASSWORD_ERROR(20002, "密码错误");

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
