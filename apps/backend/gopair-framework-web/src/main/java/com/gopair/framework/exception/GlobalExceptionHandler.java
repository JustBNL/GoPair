package com.gopair.framework.exception;

import com.gopair.common.core.R;
import com.gopair.common.exception.BaseException;
import com.gopair.common.exception.LoginException;
import com.gopair.common.exception.UserException;
import com.gopair.common.enums.impl.CommonErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * Web框架全局异常处理器 (Spring MVC)
 * 
 * 为所有MVC服务提供统一的异常处理，直接返回标准响应格式
 * 
 * @author GoPair Team
 * @since 2024-01-01
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理基础异常
     */
    @ExceptionHandler(BaseException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleBaseException(BaseException e) {
        log.error("业务异常: {}", e.getMessage(), e);
        return R.fail(e.getErrorCode(), e.getMessage());
    }

    /**
     * 处理登录异常
     */
    @ExceptionHandler(LoginException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public R<Void> handleLoginException(LoginException e) {
        log.warn("登录异常: {}", e.getMessage());
        return R.fail(e.getErrorCode(), e.getMessage());
    }
    
    /**
     * 处理用户服务异常
     */
    @ExceptionHandler(UserException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleUserException(UserException e) {
        log.error("用户服务异常: {}", e.getMessage(), e);
        return R.fail(e.getErrorCode(), e.getMessage());
    }

    /**
     * 处理参数校验异常
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleValidationException(MethodArgumentNotValidException e) {
        log.warn("参数校验失败: {}", e.getMessage());
        
        // 简化处理，只返回第一个错误信息
        String errorMessage = "参数校验失败";
        if (e.getBindingResult().hasErrors() && e.getBindingResult().getFieldError() != null) {
            errorMessage = e.getBindingResult().getFieldError().getDefaultMessage();
        }
        
        return R.fail(CommonErrorCode.PARAM_ERROR, errorMessage);
    }

    /**
     * 处理其他未知异常
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public R<Void> handleException(Exception e) {
        log.error("系统异常: {}", e.getMessage(), e);
        return R.fail(CommonErrorCode.SYSTEM_ERROR, "系统内部错误");
    }
} 