package com.gopair.gateway.enums;

import com.gopair.common.constants.MessageConstants;
import com.gopair.common.enums.ErrorCode;

/**
 * 网关专用错误码枚举
 * 
 * 定义网关层特有的错误码，主要用于认证、路由等网关核心功能
 * 错误码范围：8001-8099
 * 
 * @author gopair
 */
public enum GatewayErrorCode implements ErrorCode {

    /**
     * 认证相关错误码
     */
    TOKEN_NOT_FOUND(8001, MessageConstants.TOKEN_NOT_FOUND),
    TOKEN_VALIDATION_FAILED(8002, MessageConstants.TOKEN_VALIDATION_FAILED),
    INVALID_USER_INFO(8003, MessageConstants.INVALID_USER_INFO),
    AUTH_PROCESSING_ERROR(8004, MessageConstants.AUTH_PROCESSING_ERROR);

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
    GatewayErrorCode(int code, String message) {
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