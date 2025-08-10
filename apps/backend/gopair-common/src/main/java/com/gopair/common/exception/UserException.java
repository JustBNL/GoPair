package com.gopair.common.exception;

import com.gopair.common.enums.ErrorCode;
import lombok.Getter;

/**
 * 用户服务异常类
 *
 * 用于封装用户服务中的业务异常，继承自BaseException。
 * 如用户已存在、用户名冲突、邮箱冲突等错误情况。
 *
 * @author gopair
 */
@Getter
public class UserException extends BaseException {

    /**
     * 构造函数，接收错误码对象
     *
     * @param errorCode 错误码对象
     */
    public UserException(ErrorCode errorCode) {
        super(errorCode);
    }

    /**
     * 构造函数，接收错误码对象和自定义错误信息
     *
     * @param errorCode 错误码对象
     * @param message   自定义错误信息
     */
    public UserException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    /**
     * 构造函数，接收错误码对象和原始异常
     *
     * @param errorCode 错误码对象
     * @param cause     原始异常
     */
    public UserException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }

    /**
     * 构造函数，接收错误码对象、自定义错误信息和原始异常
     *
     * @param errorCode 错误码对象
     * @param message   自定义错误信息
     * @param cause     原始异常
     */
    public UserException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
} 