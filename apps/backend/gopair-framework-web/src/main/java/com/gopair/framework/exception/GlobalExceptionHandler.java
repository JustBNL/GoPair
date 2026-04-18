package com.gopair.framework.exception;

import com.gopair.common.constants.SystemConstants;
import com.gopair.common.core.R;
import com.gopair.common.exception.BaseException;
import com.gopair.common.enums.impl.CommonErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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
        String traceId = MDC.get(SystemConstants.MDC_TRACE_ID);
        if (e.getErrorCode() == CommonErrorCode.UNAUTHORIZED) {
            log.warn("[异常处理] [traceId={}] 认证异常: {}", traceId, e.getMessage());
        } else {
            log.warn("[异常处理] [traceId={}] 业务异常: {}", traceId, e.getMessage());
        }
        return R.fail(e.getErrorCode(), e.getMessage());
    }

    /**
     * 处理参数校验异常（收集所有字段错误）
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Map<String, String>> handleValidationException(MethodArgumentNotValidException e) {
        String traceId = MDC.get(SystemConstants.MDC_TRACE_ID);
        log.warn("[异常处理] [traceId={}] 参数校验失败: {}", traceId, e.getMessage());

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
     * 处理非法参数异常
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleIllegalArgumentException(IllegalArgumentException e) {
        String traceId = MDC.get(SystemConstants.MDC_TRACE_ID);
        log.warn("[异常处理] [traceId={}] 非法参数: {}", traceId, e.getMessage());
        return R.fail(CommonErrorCode.PARAM_ERROR, e.getMessage());
    }

    /**
     * 处理非法状态异常
     */
    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleIllegalStateException(IllegalStateException e) {
        String traceId = MDC.get(SystemConstants.MDC_TRACE_ID);
        log.warn("[异常处理] [traceId={}] 非法状态: {}", traceId, e.getMessage());
        return R.fail(CommonErrorCode.PARAM_ERROR, e.getMessage());
    }

    /**
     * 处理其他未知异常
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public R<Void> handleException(Exception e) {
        String traceId = MDC.get(SystemConstants.MDC_TRACE_ID);
        log.error("[异常处理] [traceId={}] 系统异常: {}", traceId, e.getMessage(), e);
        return R.fail(CommonErrorCode.SYSTEM_ERROR, "系统内部错误");
    }
}
