package com.gopair.userservice.enums;

import com.gopair.common.enums.ErrorCode;

/**
 * 用户服务错误码枚举类
 *
 * 实现ErrorCode接口，定义用户服务特有的错误码和错误信息。
 * 错误码规则：采用5位数字格式 A-BB-CCC
 * - A: 错误级别 (2=业务级错误)
 * - BB: 服务标识 (01=用户服务)
 * - CCC: 具体错误序号，从000开始连续递增
 * 
 * 用户服务错误码范围：20100-20199
 *
 * @author gopair
 */
public enum UserErrorCode implements ErrorCode {

    USER_NOT_FOUND(20100, "用户不存在"),
    EMAIL_ALREADY_EXISTS(20101, "邮箱已存在"),
    PASSWORD_ERROR(20102, "密码错误"),
    NICKNAME_ALREADY_EXISTS(20103, "昵称已存在"),
    VERIFICATION_CODE_INVALID(20104, "验证码无效或已过期"),
    VERIFICATION_CODE_SEND_TOO_FREQUENT(20105, "发送验证码过于频繁，请稍后再试"),
    EMAIL_NOT_EXISTS(20106, "该邮箱未注册"),
    USER_REGISTER_FAILED(20107, "用户注册失败"),
    VERIFICATION_CODE_SEND_FAILED(20108, "验证码发送失败"),
    USER_DISABLED(20109, "账号已被停用，请联系管理员"),
    USER_ALREADY_CANCELLED(20110, "账号已注销"),
    USER_CANCEL_FAILED(20111, "账号注销失败"),
    PASSWORD_SAME_AS_OLD(20112, "新密码不能与当前密码相同");

    /**
     * 错误码
     */
    private final int code;
    
    /**
     * 错误信息
     */
    private final String message;

    /**
     * 构造函数
     *
     * @param code    错误码
     * @param message 错误信息
     */
    UserErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    /**
     * 获取错误码
     *
     * @return 错误码
     */
    @Override
    public int getCode() {
        return code;
    }

    /**
     * 获取错误信息
     *
     * @return 错误信息
     */
    @Override
    public String getMessage() {
        return message;
    }
} 