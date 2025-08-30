package com.gopair.framework.logging.ops;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Endpoint(id = "logging")
public class LoggingEndpoint {

    private final DynamicLoggerManager dynamicLoggerManager;
    private final LogMetricsCollector logMetricsCollector;

    public LoggingEndpoint(DynamicLoggerManager dynamicLoggerManager,
                           LogMetricsCollector logMetricsCollector) {
        this.dynamicLoggerManager = dynamicLoggerManager;
        this.logMetricsCollector = logMetricsCollector;
    }

    @ReadOperation
    public Map<String, Object> loggingInfo() {
        Map<String, Object> info = new HashMap<>();
        try {
            info.put("timestamp", System.currentTimeMillis());
            info.put("status", "UP");
            Map<String, String> loggerLevels = dynamicLoggerManager.getAllLoggerLevels();
            info.put("loggerLevels", loggerLevels);
            info.put("loggerCount", loggerLevels.size());
            Map<String, Object> metrics = new HashMap<>();
            metrics.put("logRecordCount", logMetricsCollector.getLogRecordCount());
            metrics.put("exceptionLogCount", logMetricsCollector.getExceptionLogCount());
            metrics.put("configRefreshCount", logMetricsCollector.getConfigRefreshCount());
            info.put("metrics", metrics);
        } catch (Exception e) {
            log.error("获取日志端点信息失败", e);
            info.put("status", "DOWN");
            info.put("error", e.getMessage());
        }
        return info;
    }
} 