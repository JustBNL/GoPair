package com.gopair.framework.exception;

import com.gopair.common.core.R;
import com.gopair.common.exception.BaseException;
import com.gopair.common.enums.impl.CommonErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
    public R<Void> handleBaseException(BaseException e) {
        // 根据错误码类型设置不同的HTTP状态码
        if (e.getErrorCode().getCode() >= 800 && e.getErrorCode().getCode() < 900) {
            log.warn("认证异常: {}", e.getMessage());
        } else {
            log.warn("业务异常: {}", e.getMessage());
        }
        return R.fail(e.getErrorCode(), e.getMessage());
    }

    /**
     * 处理参数校验异常（收集所有字段错误）
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Map<String, String>> handleValidationException(MethodArgumentNotValidException e) {
        log.warn("参数校验失败: {}", e.getMessage());

        Map<String, String> errors = e.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        org.springframework.validation.FieldError::getField,
                        org.springframework.validation.FieldError::getDefaultMessage,
                        (existing, replacement) -> existing
                ));

        if (errors.isEmpty()) {
            return R.fail(CommonErrorCode.PARAM_ERROR, "参数校验失败");
        }

        return R.fail(CommonErrorCode.PARAM_ERROR.getCode(),
                errors.size() == 1 ? errors.values().iterator().next() : "存在 " + errors.size() + " 个参数错误",
                errors);
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