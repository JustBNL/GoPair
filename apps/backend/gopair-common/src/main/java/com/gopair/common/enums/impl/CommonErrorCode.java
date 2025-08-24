package com.gopair.common.enums.impl;

import com.gopair.common.constants.MessageConstants;
import com.gopair.common.enums.ErrorCode;

/**
 * 通用系统错误码枚举类
 *
 * 实现ErrorCode接口，定义系统通用的错误码和错误信息。
 * 错误码规则：
 * - 系统级错误：500-599
 * - 业务级错误：600-699
 * - 参数校验错误：700-799
 * - 授权认证错误：800-899
 *
 * @author gopair
 */
public enum CommonErrorCode implements ErrorCode {

    /**
     * 系统级错误
     */
    SYSTEM_ERROR(500, MessageConstants.SYSTEM_ERROR),
    SERVICE_UNAVAILABLE(503, MessageConstants.SERVICE_UNAVAILABLE),
    
    /**
     * 业务级错误
     */
    BUSINESS_ERROR(600, MessageConstants.BUSINESS_ERROR),
    RESOURCE_NOT_FOUND(601, MessageConstants.RESOURCE_NOT_FOUND),
    
    /**
     * 参数校验错误
     */
    PARAM_ERROR(700, MessageConstants.PARAM_ERROR),
    PARAM_MISSING(701, MessageConstants.PARAM_MISSING),
    
    /**
     * 授权认证错误
     */
    UNAUTHORIZED(800, MessageConstants.UNAUTHORIZED),
    NO_PERMISSION(803, MessageConstants.NO_PERMISSION);

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
    CommonErrorCode(int code, String message) {
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