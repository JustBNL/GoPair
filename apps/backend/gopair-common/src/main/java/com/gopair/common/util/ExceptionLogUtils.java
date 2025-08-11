package com.gopair.common.util;

import lombok.extern.slf4j.Slf4j;

/**
 * 异常日志工具类
 * 
 * 参考Logback、SLF4J等主流日志框架的设计模式，
 * 专门负责异常相关的日志记录
 * 
 * @author gopair
 */
@Slf4j
public class ExceptionLogUtils {

    /**
     * 记录业务异常日志
     * 
     * @param contextInfo 上下文信息
     * @param exceptionType 异常类型
     * @param message 异常消息
     */
    public static void logBusinessException(String contextInfo, String exceptionType, String message) {
        log.warn("[{}] 业务异常 - {}: {}", contextInfo, exceptionType, message);
    }

    /**
     * 记录系统异常日志
     * 
     * @param contextInfo 上下文信息
     * @param ex 异常对象
     */
    public static void logSystemException(String contextInfo, Throwable ex) {
        log.error("[{}] 系统异常: {}", contextInfo, ex.getMessage(), ex);
    }

    /**
     * 记录客户端异常日志
     * 
     * @param contextInfo 上下文信息
     * @param ex 异常对象
     */
    public static void logClientException(String contextInfo, Throwable ex) {
        log.warn("[{}] 客户端异常: {}", contextInfo, ex.getMessage());
    }

    /**
     * 记录网关请求异常日志
     * 
     * @param method HTTP方法
     * @param path 请求路径
     * @param ex 异常对象
     * @param httpStatus HTTP状态码
     */
    public static void logGatewayException(String method, String path, Throwable ex, int httpStatus) {
        String context = String.format("网关 %s %s", method, path);
        
        if (httpStatus >= 500) {
            log.error("[{}] 服务器错误({}): {}", context, httpStatus, ex.getMessage(), ex);
        } else if (httpStatus >= 400) {
            log.warn("[{}] 客户端错误({}): {}", context, httpStatus, ex.getMessage());
        } else {
            log.info("[{}] 处理信息({}): {}", context, httpStatus, ex.getMessage());
        }
    }

    /**
     * 根据异常类型智能选择日志级别
     * 
     * @param contextInfo 上下文信息
     * @param ex 异常对象
     */
    public static void logSmartly(String contextInfo, Throwable ex) {
        if (isSystemException(ex)) {
            logSystemException(contextInfo, ex);
        } else {
            logClientException(contextInfo, ex);
        }
    }

    /**
     * 判断是否为系统级异常
     * 
     * @param ex 异常对象
     * @return 是否为系统异常
     */
    private static boolean isSystemException(Throwable ex) {
        String className = ex.getClass().getName();
        return className.contains("SQLException") ||
               className.contains("IOException") ||
               className.contains("RuntimeException") ||
               className.contains("NullPointerException") ||
               className.contains("ClassCastException") ||
               className.contains("IllegalState") ||
               ex instanceof Error;
    }
} 