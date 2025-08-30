package com.gopair.framework.logging.aspect;

import com.gopair.framework.config.properties.LoggingProperties;
import com.gopair.framework.logging.annotation.LogRecord;
import com.gopair.framework.context.UserContextHolder;
import com.gopair.framework.logging.ops.LogMetricsCollector;
import com.gopair.framework.logging.event.LogRecordEvent;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.StringJoiner;

/**
 * 日志记录切面
 *
 * 记录标记了 @LogRecord 注解的方法的操作
 *
 * @author gopair
 */
@Slf4j
@Aspect
@Component
public class LogRecordAspect {

    private final LoggingProperties loggingProperties;
    private final LogMetricsCollector logMetricsCollector;
    private final ApplicationEventPublisher eventPublisher;

    public LogRecordAspect(LoggingProperties loggingProperties,
                           LogMetricsCollector logMetricsCollector,
                           ApplicationEventPublisher eventPublisher) {
        this.loggingProperties = loggingProperties;
        this.logMetricsCollector = logMetricsCollector;
        this.eventPublisher = eventPublisher;
    }

    @Around("@annotation(logRecord)")
    public Object logOperation(ProceedingJoinPoint joinPoint, LogRecord logRecord) throws Throwable {
        // 根据配置决定是否需要记录性能
        boolean shouldLogPerformance = logRecord.logPerformance() && loggingProperties.getAop().isLogExecutionTime();
        long startTime = shouldLogPerformance ? System.currentTimeMillis() : 0;
        
        String module = logRecord.module().isEmpty() ? joinPoint.getTarget().getClass().getSimpleName() : logRecord.module();
        
        // 记录方法开始执行
        logStart(joinPoint, logRecord, module);

        Object result = null;
        Throwable exception = null;
        try {
            result = joinPoint.proceed();
            return result;
        } catch (Throwable e) {
            exception = e;
            throw e;
        } finally {
            long duration = shouldLogPerformance ? (System.currentTimeMillis() - startTime) : 0;
            // 记录方法执行结束
            logEnd(joinPoint, logRecord, module, duration, result, exception, shouldLogPerformance);
        }
    }

    private void logStart(ProceedingJoinPoint joinPoint, LogRecord logRecord, String module) {
        try {
            StringJoiner logMessage = new StringJoiner(" - ", "[业务日志] 开始执行", "");
            logMessage.add("模块: " + module);
            logMessage.add("操作: " + logRecord.operation());
            
            // 根据配置和注解决定是否记录参数
            boolean shouldLogArgs = logRecord.includeArgs() && loggingProperties.getAop().isLogArgs();
            if (shouldLogArgs) {
                logMessage.add("参数: " + formatMethodArgs(joinPoint.getArgs()));
            }
            log.info(logMessage.toString());
        } catch (Exception e) {
            log.error("[业务日志] 记录开始日志时发生异常", e);
        }
    }

    private void logEnd(ProceedingJoinPoint joinPoint, LogRecord logRecord, String module,
                        long duration, Object result, Throwable exception, boolean shouldLogPerformance) {
        try {
            String status = (exception == null) ? "执行成功" : "执行失败";
            StringJoiner logMessage = new StringJoiner(" - ", "[业务日志] " + status, "");

            logMessage.add("模块: " + module);
            logMessage.add("操作: " + logRecord.operation());
            
            // 根据传入的性能记录标志决定是否记录执行时间
            if (shouldLogPerformance) {
                logMessage.add("耗时: " + duration + "ms");
            }
            
            // 根据配置和注解决定是否记录返回结果
            boolean shouldLogResult = logRecord.includeResult() && loggingProperties.getAop().isLogResult();
            if (shouldLogResult && result != null) {
                logMessage.add("结果: " + result.toString());
            }

            if (exception != null) {
                log.error("{} - 异常: {}", logMessage.toString(), exception.getMessage(), exception);
            } else {
                log.info(logMessage.toString());
            }
        } catch (Exception e) {
            log.error("[业务日志] 记录结束日志时发生异常", e);
        }
    }

    private void safeRecordLog() {
        try {
            // 这里可以根据需要决定是记录业务日志还是性能日志，或两者都记录
            // 暂时保留业务日志的记录
            logMetricsCollector.recordLog();
        } catch (Throwable ignore) {}
    }

    private void safePublishEvent(Object event) {
        try {
            if (eventPublisher != null) {
                eventPublisher.publishEvent(event);
            }
        } catch (Throwable ignore) {}
    }

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