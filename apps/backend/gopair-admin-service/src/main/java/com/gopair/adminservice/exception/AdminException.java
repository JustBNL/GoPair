package com.gopair.adminservice.exception;

import com.gopair.common.enums.ErrorCode;
import com.gopair.common.exception.BaseException;

/**
 * 管理员业务异常类
 *
 * 继承自BaseException，用于封装管理员模块的业务校验失败场景。
 * 由于继承BaseException，ExceptionLogAspect 会将其识别为"业务异常"并以 WARN 级别记录，
 * 区别于系统异常（ERROR 级别）。
 *
 * @author gopair
 */
public class AdminException extends BaseException {

    public AdminException(ErrorCode errorCode) {
        super(errorCode);
    }

    public AdminException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public AdminException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }

    public AdminException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
