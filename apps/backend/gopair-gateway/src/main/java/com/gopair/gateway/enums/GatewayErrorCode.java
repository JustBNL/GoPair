package com.gopair.gateway.enums;

import com.gopair.common.enums.ErrorCode;

/**
 * 网关服务错误码枚举类
 * 
 * 实现ErrorCode接口，定义网关层特有的错误码和错误信息。
 * 错误码规则：采用5位数字格式 A-BB-CCC
 * - A: 错误级别 (2=业务级错误)
 * - BB: 服务标识 (03=网关服务)
 * - CCC: 具体错误序号，从000开始连续递增
 * 
 * 网关服务错误码范围：20300-20399
 * 
 * @author gopair
 */
public enum GatewayErrorCode implements ErrorCode {

    /**
     * 认证相关错误码
     */
    TOKEN_NOT_FOUND(20300, "未找到认证令牌"),
    TOKEN_VALIDATION_FAILED(20301, "令牌验证失败"),
    INVALID_USER_INFO(20302, "无效的用户信息"),
    AUTH_PROCESSING_ERROR(20303, "认证处理异常");

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