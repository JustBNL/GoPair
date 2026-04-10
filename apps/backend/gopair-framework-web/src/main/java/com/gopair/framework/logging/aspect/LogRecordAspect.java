package com.gopair.framework.logging.aspect;

import com.gopair.common.exception.BaseException;
import com.gopair.framework.config.properties.LoggingProperties;
import com.gopair.framework.logging.annotation.LogRecord;
import com.gopair.framework.logging.ops.LogMetricsCollector;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

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

    public LogRecordAspect(LoggingProperties loggingProperties,
                           @Autowired(required = false) LogMetricsCollector logMetricsCollector) {
        this.loggingProperties = loggingProperties;
        this.logMetricsCollector = logMetricsCollector;
    }

    @Around("@annotation(logRecord)")
    public Object logOperation(ProceedingJoinPoint joinPoint, LogRecord logRecord) throws Throwable {
        boolean shouldLogPerformance = logRecord.logPerformance() && loggingProperties.getAop().isLogExecutionTime();
        long startTime = shouldLogPerformance ? System.currentTimeMillis() : 0;

        String module = logRecord.module().isEmpty() ? joinPoint.getTarget().getClass().getSimpleName() : logRecord.module();

        logStart(joinPoint, logRecord, module);

        Object result = null;
        Throwable exception = null;
        try {
            result = joinPoint.proceed();
        } catch (Throwable e) {
            exception = e;
            throw e;
        } finally {
            long duration = shouldLogPerformance ? (System.currentTimeMillis() - startTime) : 0;
            logEnd(joinPoint, logRecord, module, duration, result, exception, shouldLogPerformance);
            if (logMetricsCollector != null) {
                logMetricsCollector.recordLog();
            }
        }
        return result;
    }

    private void logStart(ProceedingJoinPoint joinPoint, LogRecord logRecord, String module) {
        try {
            StringJoiner logMessage = new StringJoiner(" - ", "[业务日志] 开始执行", "");
            logMessage.add("模块: " + module);
            logMessage.add("操作: " + logRecord.operation());

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

            if (shouldLogPerformance) {
                logMessage.add("耗时: " + duration + "ms");
            }

            boolean shouldLogResult = logRecord.includeResult() && loggingProperties.getAop().isLogResult();
            if (shouldLogResult && result != null) {
                logMessage.add("结果: " + result.toString());
            }

            if (exception != null) {
                if (exception instanceof BaseException) {
                    log.warn("{} - 异常: {}", logMessage.toString(), exception.getMessage());
                } else {
                    log.error("{} - 异常: {}", logMessage.toString(), exception.getMessage(), exception);
                }
            } else {
                log.info(logMessage.toString());
            }
        } catch (Exception e) {
            log.error("[业务日志] 记录结束日志时发生异常", e);
        }
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
