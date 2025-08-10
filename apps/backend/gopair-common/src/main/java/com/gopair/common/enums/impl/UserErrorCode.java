package com.gopair.common.enums.impl;

import com.gopair.common.constants.MessageConstants;
import com.gopair.common.enums.ErrorCode;

/**
 * 用户服务错误码枚举类
 *
 * 实现ErrorCode接口，定义用户服务特有的错误码和错误信息。
 * 用户服务错误码规则：
 * - 用户错误码: 1001-1099
 *
 * @author gopair
 */
public enum UserErrorCode implements ErrorCode {

    /**
     * 用户不存在
     */
    USER_NOT_FOUND(1001, MessageConstants.USER_NOT_FOUND),
    
    /**
     * 用户已存在
     */
    USER_ALREADY_EXISTS(1002, MessageConstants.USER_ALREADY_EXISTS),
    
    /**
     * 用户名已存在
     */
    USERNAME_ALREADY_EXISTS(1003, MessageConstants.USERNAME_ALREADY_EXISTS),
    
    /**
     * 邮箱已存在
     */
    EMAIL_ALREADY_EXISTS(1004, MessageConstants.EMAIL_ALREADY_EXISTS),
    
    /**
     * 凭证无效
     */
    INVALID_CREDENTIALS(1005, MessageConstants.INVALID_CREDENTIALS),
    
    /**
     * 密码错误
     */
    PASSWORD_ERROR(1006, MessageConstants.PASSWORD_ERROR);

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