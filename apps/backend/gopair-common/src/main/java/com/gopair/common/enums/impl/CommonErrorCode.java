package com.gopair.common.enums.impl;

import com.gopair.common.enums.ErrorCode;

/**
 * 通用系统错误码枚举类
 *
 * 实现ErrorCode接口，定义系统通用的错误码和错误信息。
 * 错误码规则：采用5位数字格式 A-BB-CCC
 * - A: 错误级别 (1=系统级通用错误)
 * - BB: 服务标识 (00=通用)
 * - CCC: 具体错误序号，从000开始连续递增
 * 
 * 系统级通用错误范围：10000-10999
 *
 * @author gopair
 */
public enum CommonErrorCode implements ErrorCode {

    /**
     * 系统级错误
     */
    SYSTEM_ERROR(10000, "系统内部错误"),
    SERVICE_UNAVAILABLE(10001, "服务不可用"),
    
    /**
     * 参数校验错误
     */
    PARAM_ERROR(10002, "参数错误"),
    PARAM_MISSING(10003, "缺少必要参数"),
    
    /**
     * 授权认证错误
     */
    UNAUTHORIZED(10004, "未授权访问"),
    
    /**
     * 业务级错误
     */
    BUSINESS_ERROR(10005, "业务处理异常"),
    RESOURCE_NOT_FOUND(10006, "请求资源不存在");

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