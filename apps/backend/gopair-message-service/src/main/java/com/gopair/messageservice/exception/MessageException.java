package com.gopair.messageservice.exception;

import com.gopair.common.enums.ErrorCode;
import com.gopair.common.exception.BaseException;

/**
 * 消息服务异常类
 *
 * 用于封装消息服务中的业务异常，继承自BaseException。
 * 如消息发送失败、消息不存在、权限不足等错误情况。
 *
 * @author gopair
 */
public class MessageException extends BaseException {

    /**
     * 构造函数，接收错误码对象
     *
     * @param errorCode 错误码对象
     */
    public MessageException(ErrorCode errorCode) {
        super(errorCode);
    }

    /**
     * 构造函数，接收错误码对象和自定义错误信息
     *
     * @param errorCode 错误码对象
     * @param message   自定义错误信息
     */
    public MessageException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    /**
     * 构造函数，接收错误码对象和原始异常
     *
     * @param errorCode 错误码对象
     * @param cause     原始异常
     */
    public MessageException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }

    /**
     * 构造函数，接收错误码对象、自定义错误信息和原始异常
     *
     * @param errorCode 错误码对象
     * @param message   自定义错误信息
     * @param cause     原始异常
     */
    public MessageException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
} 