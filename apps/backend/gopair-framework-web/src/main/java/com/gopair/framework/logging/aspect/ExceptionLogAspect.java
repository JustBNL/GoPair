package com.gopair.framework.logging.aspect;

import com.gopair.framework.config.properties.LoggingProperties;
import com.gopair.framework.context.UserContextHolder;
import com.gopair.framework.logging.ops.LogMetricsCollector;
import com.gopair.framework.logging.event.ExceptionLogEvent;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 异常日志切面
 * 
 * 自动记录所有Controller和Service层的异常
 * 
 * @author gopair
 */
@Slf4j
@Aspect
@Component
@Order(300)
public class ExceptionLogAspect {
    
    private final LoggingProperties loggingProperties;
    private final LogMetricsCollector logMetricsCollector;
    private final ApplicationEventPublisher eventPublisher;

    public ExceptionLogAspect(LoggingProperties loggingProperties,
                              LogMetricsCollector logMetricsCollector,
                              ApplicationEventPublisher eventPublisher) {
        this.loggingProperties = loggingProperties;
        this.logMetricsCollector = logMetricsCollector;
        this.eventPublisher = eventPublisher;
    }
    
    /**
     * Controller层异常记录
     */
    @AfterThrowing(pointcut = "execution(* com.gopair..controller..*(..))", throwing = "ex")
    public void logControllerException(JoinPoint joinPoint, Throwable ex) {
        logException(joinPoint, ex, "CONTROLLER");
    }
    
    /**
     * Service层异常记录
     */
    @AfterThrowing(pointcut = "execution(* com.gopair..service..*(..))", throwing = "ex")
    public void logServiceException(JoinPoint joinPoint, Throwable ex) {
        logException(joinPoint, ex, "SERVICE");
    }
    
    /**
     * 统一异常日志记录
     */
    private void logException(JoinPoint joinPoint, Throwable ex, String layer) {
        String methodName = joinPoint.getSignature().getName();
        String className = joinPoint.getTarget().getClass().getSimpleName();
        
        // 获取用户上下文
        Long userId = UserContextHolder.getCurrentUserId();
        String nickname = UserContextHolder.getCurrentNickname();
        
        // 根据配置决定是否记录方法参数
        String argsString = "";
        if (loggingProperties.getAop().isLogArgs()) {
            Object[] args = joinPoint.getArgs();
            argsString = formatMethodArgs(args);
        } else {
            argsString = "[参数记录已禁用]";
        }
        
        // 指标与事件
        safeRecordExceptionLog();
        safePublishEvent(new ExceptionLogEvent(this));
        
        // 记录异常日志
        log.error("[异常日志] {}层异常 - 类: {}, 方法: {}, 参数: {}, 用户ID: {}, 昵称: {}, 异常类型: {}, 异常消息: {}", 
                 layer, className, methodName, argsString, userId, nickname, 
                 ex.getClass().getSimpleName(), ex.getMessage(), ex);
    }
    
    private void safeRecordExceptionLog() {
        try {
            logMetricsCollector.recordExceptionLog();
        } catch (Throwable ignore) {}
    }

    private void safePublishEvent(Object event) {
        try {
            if (eventPublisher != null) {
                eventPublisher.publishEvent(event);
            }
        } catch (Throwable ignore) {}
    }
    
    /**
     * 格式化方法参数
     */
    private String formatMethodArgs(Object[] args) {
        if (args == null || args.length == 0) {
            return "()";
        }
        
        StringBuilder sb = new StringBuilder("(");
        for (int i = 0; i < args.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append("arg").append(i).append("=").append(args[i] != null ? args[i].toString() : "null");
        }
        sb.append(")");
        return sb.toString();
    }
} 