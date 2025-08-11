package com.gopair.common.exception;

import com.gopair.common.core.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器 (Spring MVC)
 * 
 * 统一处理微服务中抛出的各种异常，使用ExceptionHandlerHelper协调处理
 * 
 * @author GoPair Team
 * @since 2024-01-01
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String CONTEXT = "微服务";

    /**
     * 处理基础异常
     */
    @ExceptionHandler(BaseException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleBaseException(BaseException e) {
        return ExceptionHandlerHelper.handleBaseException(e, CONTEXT);
    }

    /**
     * 处理登录异常
     */
    @ExceptionHandler(LoginException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public R<Void> handleLoginException(LoginException e) {
        return ExceptionHandlerHelper.handleLoginException(e, CONTEXT);
    }
    
    /**
     * 处理用户服务异常
     */
    @ExceptionHandler(UserException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleUserException(UserException e) {
        return ExceptionHandlerHelper.handleUserException(e, CONTEXT);
    }

    /**
     * 处理参数校验异常
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleValidationException(MethodArgumentNotValidException e) {
        return ExceptionHandlerHelper.handleValidationException(e, CONTEXT);
    }

    /**
     * 处理其他未知异常
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public R<Void> handleException(Exception e) {
        return ExceptionHandlerHelper.handleGenericException(e, CONTEXT);
    }
} 