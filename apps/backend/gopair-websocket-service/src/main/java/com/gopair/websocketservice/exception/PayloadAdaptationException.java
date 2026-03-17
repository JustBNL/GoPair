package com.gopair.websocketservice.exception;

import com.gopair.common.exception.BaseException;
import com.gopair.common.enums.ErrorCode;

/**
 * 载荷适配异常
 * 当载荷转换过程中发生错误时抛出
 * 
 * @author gopair
 */
public class PayloadAdaptationException extends BaseException {

    /**
     * 构造函数
     * 
     * @param errorCode 错误码对象
     */
    public PayloadAdaptationException(ErrorCode errorCode) {
        super(errorCode);
    }

    /**
     * 构造函数
     * 
     * @param errorCode 错误码对象
     * @param message 自定义错误信息
     */
    public PayloadAdaptationException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    /**
     * 构造函数
     * 
     * @param errorCode 错误码对象
     * @param cause 原始异常
     */
    public PayloadAdaptationException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }

    /**
     * 构造函数
     * 
     * @param errorCode 错误码对象
     * @param message 自定义错误信息
     * @param cause 原始异常
     */
    public PayloadAdaptationException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
} 