package com.gopair.common.exception;

import com.gopair.common.core.R;
import com.gopair.common.enums.ErrorCode;
import com.gopair.common.enums.impl.CommonErrorCode;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.stream.Collectors;

/**
 * 异常解析器
 * 
 * 参考Spring HandlerExceptionResolver设计模式，
 * 专门负责将各种异常解析为对应的错误码和响应
 * 
 * @author gopair
 */
public class ExceptionResolver {

    /**
     * 解析异常并构建响应
     * 
     * @param ex 异常对象
     * @return 响应结果
     */
    public static R<Void> resolve(Throwable ex) {
        // 业务异常直接返回
        if (ex instanceof BaseException) {
            BaseException baseEx = (BaseException) ex;
            return R.fail(baseEx.getErrorCode());
        }

        // 参数校验异常特殊处理
        if (ex instanceof MethodArgumentNotValidException) {
            MethodArgumentNotValidException validEx = (MethodArgumentNotValidException) ex;
            String errorMessage = validEx.getBindingResult().getFieldErrors().stream()
                    .map(FieldError::getDefaultMessage)
                    .collect(Collectors.joining(", "));
            return R.fail(CommonErrorCode.PARAM_ERROR, errorMessage);
        }

        // 根据异常类型映射错误码
        ErrorCode errorCode = mapExceptionToErrorCode(ex);
        return R.fail(errorCode);
    }

    /**
     * 解析异常并使用自定义消息
     * 
     * @param ex 异常对象
     * @param customMessage 自定义消息
     * @return 响应结果
     */
    public static R<Void> resolve(Throwable ex, String customMessage) {
        // 业务异常保留错误码，使用自定义消息
        if (ex instanceof BaseException) {
            BaseException baseEx = (BaseException) ex;
            return R.fail(baseEx.getErrorCode(), customMessage);
        }

        // 其他异常映射错误码
        ErrorCode errorCode = mapExceptionToErrorCode(ex);
        return R.fail(errorCode, customMessage);
    }

    /**
     * 仅获取错误码（不构建响应）
     * 
     * @param ex 异常对象
     * @return 错误码
     */
    public static ErrorCode getErrorCode(Throwable ex) {
        if (ex instanceof BaseException) {
            return ((BaseException) ex).getErrorCode();
        }
        return mapExceptionToErrorCode(ex);
    }

    /**
     * 将异常映射为错误码
     * 
     * @param ex 异常对象
     * @return 错误码
     */
    private static ErrorCode mapExceptionToErrorCode(Throwable ex) {
        String className = ex.getClass().getName();

        // 认证相关异常
        if (isAuthenticationException(className)) {
            return CommonErrorCode.UNAUTHORIZED;
        }

        // 权限相关异常
        if (isAccessDeniedException(className)) {
            return CommonErrorCode.ACCESS_DENIED;
        }

        // 参数相关异常
        if (isParameterException(className)) {
            return CommonErrorCode.PARAM_ERROR;
        }

        // 资源不存在异常
        if (isNotFoundException(className, ex)) {
            return CommonErrorCode.RESOURCE_NOT_FOUND;
        }

        // 服务不可用异常
        if (isServiceUnavailableException(className)) {
            return CommonErrorCode.SERVICE_UNAVAILABLE;
        }

        // 默认系统错误
        return CommonErrorCode.SYSTEM_ERROR;
    }

    // ==================== 异常类型判断方法 ====================

    private static boolean isAuthenticationException(String className) {
        return className.contains("Authentication") ||
               className.contains("Unauthorized") ||
               className.contains("Token") ||
               className.contains("LoginException");
    }

    private static boolean isAccessDeniedException(String className) {
        return className.contains("AccessDenied") ||
               className.contains("Forbidden");
    }

    private static boolean isParameterException(String className) {
        return className.contains("MethodArgumentNotValid") ||
               className.contains("ConstraintViolation") ||
               className.contains("BindException") ||
               className.contains("ServerWebInputException") ||
               className.contains("ValidationException");
    }

    private static boolean isNotFoundException(String className, Throwable ex) {
        return className.contains("NotFound") ||
               className.contains("NoSuchElement") ||
               (ex.getMessage() != null && ex.getMessage().contains("404"));
    }

    private static boolean isServiceUnavailableException(String className) {
        return className.contains("ConnectException") ||
               className.contains("TimeoutException") ||
               className.contains("ServiceUnavailable") ||
               className.contains("CircuitBreaker");
    }
} 