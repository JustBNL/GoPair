package com.gopair.backend.handler;

import com.gopair.backend.common.enums.ErrorCode;
import com.gopair.backend.common.exception.BusinessException;
import com.gopair.backend.common.vo.ResultVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 全局异常处理器
 *
 * 用于捕获所有Controller抛出的异常，并将其转换为统一的ResultVO格式返回给前端。
 * 通过@RestControllerAdvice注解，Spring会自动将其应用到所有的Controller。
 *
 *
 * @author gopair
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 处理业务异常
     *
     * 捕获自定义的BusinessException，记录警告日志，并返回对应的错误信息
     *
     *
     * @param e 业务异常
     * @return 统一的错误响应
     */
    @ExceptionHandler(BusinessException.class)
    public ResultVO<Void> handleBusinessException(BusinessException e) {
        logger.warn("业务异常: {}", e.getMessage());
        return ResultVO.error(e.getErrorCode());
    }

    /**
     * 处理参数校验异常 (JSR-303 @Valid 注解校验抛出的异常)
     *
     * @param e 参数校验异常
     * @return 统一的错误响应
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResultVO<Void> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        BindingResult bindingResult = e.getBindingResult();
        String message = bindingResult.getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        logger.warn("参数校验异常: {}", message);
        return ResultVO.error(ErrorCode.PARAM_ERROR.getCode(), message);
    }

    /**
     * 处理参数绑定异常
     *
     * @param e 参数绑定异常
     * @return 统一的错误响应
     */
    @ExceptionHandler(BindException.class)
    public ResultVO<Void> handleBindException(BindException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        logger.warn("参数绑定异常: {}", message);
        return ResultVO.error(ErrorCode.PARAM_BIND_ERROR.getCode(), message);
    }

    /**
     * 处理约束违反异常
     *
     * @param e 约束违反异常
     * @return 统一的错误响应
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResultVO<Void> handleConstraintViolationException(ConstraintViolationException e) {
        Set<ConstraintViolation<?>> violations = e.getConstraintViolations();
        String message = violations.stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining(", "));
        logger.warn("约束违反异常: {}", message);
        return ResultVO.error(ErrorCode.PARAM_ERROR.getCode(), message);
    }

    /**
     * 处理所有其他未被捕获的异常
     *
     * 捕获所有其他未被处理的异常，记录错误日志，并返回通用的系统错误信息
     *
     *
     * @param e 异常
     * @return 统一的错误响应
     */
    @ExceptionHandler(Exception.class)
    public ResultVO<Void> handleException(Exception e) {
        logger.error("系统未知异常: {}", e.getMessage(), e);
        return ResultVO.error(ErrorCode.SYSTEM_ERROR);
    }
} 