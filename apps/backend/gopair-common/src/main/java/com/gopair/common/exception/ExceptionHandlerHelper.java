package com.gopair.common.exception;

import com.gopair.common.core.R;
import com.gopair.common.util.ExceptionLogUtils;

/**
 * 异常处理协调器
 * 
 * 参考Facade设计模式，作为异常处理的统一入口，
 * 协调ExceptionResolver和ExceptionLogUtils完成异常处理
 * 
 * @author gopair
 */
public class ExceptionHandlerHelper {

    /**
     * 处理基础业务异常
     * 
     * @param e 基础异常
     * @param contextInfo 上下文信息
     * @return 统一错误响应
     */
    public static R<Void> handleBaseException(BaseException e, String contextInfo) {
        ExceptionLogUtils.logBusinessException(contextInfo, "BaseException", e.getMessage());
        return ExceptionResolver.resolve(e);
    }

    /**
     * 处理登录异常
     * 
     * @param e 登录异常
     * @param contextInfo 上下文信息
     * @return 统一错误响应
     */
    public static R<Void> handleLoginException(LoginException e, String contextInfo) {
        ExceptionLogUtils.logBusinessException(contextInfo, "LoginException", e.getMessage());
        return ExceptionResolver.resolve(e);
    }

    /**
     * 处理用户异常
     * 
     * @param e 用户异常
     * @param contextInfo 上下文信息
     * @return 统一错误响应
     */
    public static R<Void> handleUserException(UserException e, String contextInfo) {
        ExceptionLogUtils.logBusinessException(contextInfo, "UserException", e.getMessage());
        return ExceptionResolver.resolve(e);
    }

    /**
     * 处理参数校验异常
     * 
     * @param e 参数校验异常
     * @param contextInfo 上下文信息
     * @return 统一错误响应
     */
    public static R<Void> handleValidationException(Exception e, String contextInfo) {
        ExceptionLogUtils.logClientException(contextInfo, e);
        return ExceptionResolver.resolve(e);
    }

    /**
     * 处理通用异常
     * 
     * @param e 异常对象
     * @param contextInfo 上下文信息
     * @return 统一错误响应
     */
    public static R<Void> handleGenericException(Throwable e, String contextInfo) {
        ExceptionLogUtils.logSmartly(contextInfo, e);
        return ExceptionResolver.resolve(e);
    }

    /**
     * 处理网关异常（专用）
     * 
     * @param ex 异常对象
     * @param method HTTP方法
     * @param path 请求路径
     * @param httpStatus HTTP状态码
     * @return 统一错误响应
     */
    public static R<Void> handleGatewayException(Throwable ex, String method, String path, int httpStatus) {
        ExceptionLogUtils.logGatewayException(method, path, ex, httpStatus);
        return ExceptionResolver.resolve(ex);
    }
} 