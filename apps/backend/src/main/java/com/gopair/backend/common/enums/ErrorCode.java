package com.gopair.backend.common.enums;

import com.gopair.backend.common.IErrorCode;

/**
 * 系统错误码枚举类
 *
 * 实现IErrorCode接口，统一定义系统中的错误码和错误信息。
 * 错误码规则：
 * - 系统级错误：500-599
 * - 业务级错误：600-699
 * - 参数校验错误：700-799
 * - 授权认证错误：800-899
 *
 * @author gopair
 */
public enum ErrorCode implements IErrorCode {

    /**
     * 系统级错误
     */
    SYSTEM_ERROR(500, "系统内部错误"),
    SERVICE_UNAVAILABLE(503, "服务不可用"),
    
    /**
     * 业务级错误
     */
    BUSINESS_ERROR(600, "业务处理异常"),
    RESOURCE_NOT_FOUND(601, "请求资源不存在"),
    OPERATION_FAILED(602, "操作失败"),
    
    /**
     * 参数校验错误
     */
    PARAM_ERROR(700, "参数错误"),
    PARAM_MISSING(701, "缺少必要参数"),
    PARAM_TYPE_ERROR(702, "参数类型错误"),
    PARAM_BIND_ERROR(703, "参数绑定错误"),
    
    /**
     * 授权认证错误
     */
    UNAUTHORIZED(800, "未授权访问"),
    TOKEN_EXPIRED(801, "令牌已过期"),
    TOKEN_INVALID(802, "无效的令牌"),
    ACCESS_DENIED(803, "访问被拒绝");

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
    ErrorCode(int code, String message) {
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
