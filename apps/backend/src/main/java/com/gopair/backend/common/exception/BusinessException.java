package com.gopair.backend.common.exception;

import com.gopair.backend.common.IErrorCode;
import lombok.Getter;

/**
 * 业务异常类
 *
 * 用于封装业务逻辑异常，继承自RuntimeException使其成为非受检异常，
 * 这样在业务代码中就不需要显式地进行try-catch处理。
 * 同时通过包含IErrorCode对象，可以携带具体的错误码和错误信息。
 *
 *
 * @author gopair
 */
@Getter
public class BusinessException extends RuntimeException {

    /**
     * 错误码对象，包含错误码和错误信息
     */
    private final IErrorCode errorCode;

    /**
     * 构造函数，接收错误码对象
     *
     * @param errorCode 错误码对象，实现了IErrorCode接口
     */
    public BusinessException(IErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    /**
     * 构造函数，接收错误码对象和自定义错误信息
     *
     * @param errorCode 错误码对象，实现了IErrorCode接口
     * @param message   自定义错误信息
     */
    public BusinessException(IErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * 构造函数，接收错误码对象和原始异常
     *
     * @param errorCode 错误码对象，实现了IErrorCode接口
     * @param cause     原始异常
     */
    public BusinessException(IErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
    }

    /**
     * 构造函数，接收错误码对象、自定义错误信息和原始异常
     *
     * @param errorCode 错误码对象，实现了IErrorCode接口
     * @param message   自定义错误信息
     * @param cause     原始异常
     */
    public BusinessException(IErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
} 