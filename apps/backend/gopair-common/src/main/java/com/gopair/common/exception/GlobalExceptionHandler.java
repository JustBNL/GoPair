package com.gopair.common.exception;

import com.gopair.common.core.R;
import com.gopair.common.enums.impl.CommonErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.stream.Collectors;

/**
 * 全局异常处理器
 * 
 * 统一处理系统中抛出的各种异常，返回标准化的错误响应
 * 
 * @author GoPair Team
 * @since 2024-01-01
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理基础异常
     * 
     * @param e 基础异常
     * @return 错误响应
     */
    @ExceptionHandler(BaseException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleBaseException(BaseException e) {
        log.warn("基础异常: {}", e.getMessage());
        return R.fail(e.getErrorCode());
    }

    /**
     * 处理登录异常
     * 
     * @param e 登录异常
     * @return 错误响应
     */
    @ExceptionHandler(LoginException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public R<Void> handleLoginException(LoginException e) {
        log.warn("登录异常: {}", e.getMessage());
        return R.fail(e.getErrorCode());
    }

    /**
     * 处理其他未知异常
     * 
     * @param e 未知异常
     * @return 错误响应
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public R<Void> handleException(Exception e) {
        log.error("系统异常: ", e);
        return R.fail(CommonErrorCode.SYSTEM_ERROR);
    }
} 