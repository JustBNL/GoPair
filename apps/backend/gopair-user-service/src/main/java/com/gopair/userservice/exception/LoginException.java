package com.gopair.userservice.exception;

import com.gopair.common.enums.ErrorCode;
import com.gopair.common.exception.BaseException;

/**
 * 登录异常类
 *
 * 用于封装登录、认证过程中的异常情况，继承自BaseException。
 * 可以用于表示用户名不存在、密码错误、账户锁定等登录相关异常。
 *
 * @author gopair
 */
public class LoginException extends BaseException {

    /**
     * 构造函数，接收错误码对象
     *
     * @param errorCode 错误码对象
     */
    public LoginException(ErrorCode errorCode) {
        super(errorCode);
    }

    /**
     * 构造函数，接收错误码对象和自定义错误信息
     *
     * @param errorCode 错误码对象
     * @param message   自定义错误信息
     */
    public LoginException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    /**
     * 构造函数，接收错误码对象和原始异常
     *
     * @param errorCode 错误码对象
     * @param cause     原始异常
     */
    public LoginException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }

    /**
     * 构造函数，接收错误码对象、自定义错误信息和原始异常
     *
     * @param errorCode 错误码对象
     * @param message   自定义错误信息
     * @param cause     原始异常
     */
    public LoginException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
} 