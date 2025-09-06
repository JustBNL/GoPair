package com.gopair.websocketservice.exception;

/**
 * 载荷适配异常
 * 当载荷转换过程中发生错误时抛出
 * 
 * @author gopair
 */
public class PayloadAdaptationException extends RuntimeException {

    /**
     * 构造函数
     * 
     * @param message 异常消息
     */
    public PayloadAdaptationException(String message) {
        super(message);
    }

    /**
     * 构造函数
     * 
     * @param message 异常消息
     * @param cause 原始异常
     */
    public PayloadAdaptationException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * 构造函数
     * 
     * @param cause 原始异常
     */
    public PayloadAdaptationException(Throwable cause) {
        super(cause);
    }
} 