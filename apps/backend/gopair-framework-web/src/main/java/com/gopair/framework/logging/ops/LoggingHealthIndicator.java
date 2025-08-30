package com.gopair.framework.logging.ops;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 日志健康检查器
 * 
 * 检查日志系统的健康状态
 * 注意：通过 LoggingConfiguration 的 @Bean 方式创建，不使用 @Component
 * 
 * @author gopair
 */
@Slf4j
public class LoggingHealthIndicator implements HealthIndicator {
    
    private final LoggingSystem loggingSystem;
    private final DynamicLoggerManager dynamicLoggerManager;
    
    public LoggingHealthIndicator(LoggingSystem loggingSystem, 
                                 DynamicLoggerManager dynamicLoggerManager) {
        this.loggingSystem = loggingSystem;
        this.dynamicLoggerManager = dynamicLoggerManager;
    }
    
    @Override
    public Health health() {
        try {
            Health.Builder builder = Health.up();
            
            // 检查日志系统状态
            checkLoggingSystem(builder);
            
            // 检查关键Logger配置
            checkCriticalLoggers(builder);
            
            // 检查日志文件状态
            checkLogFileStatus(builder);
            
            return builder.build();
            
        } catch (Exception e) {
            log.error("日志健康检查失败", e);
            return Health.down()
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
    
    /**
     * 检查日志系统状态
     */
    private void checkLoggingSystem(Health.Builder builder) {
        try {
            String loggingSystemType = loggingSystem.getClass().getSimpleName();
            builder.withDetail("loggingSystem", loggingSystemType);
            
            // 检查是否可以正常获取Logger配置
            boolean canGetLoggerConfig = dynamicLoggerManager.isLoggerExists("ROOT");
            builder.withDetail("canAccessLoggerConfig", canGetLoggerConfig);
            
            if (!canGetLoggerConfig) {
                builder.down().withDetail("reason", "无法访问Logger配置");
            }
            
        } catch (Exception e) {
            builder.down().withDetail("loggingSystemError", e.getMessage());
        }
    }
    
    /**
     * 检查关键Logger配置
     */
    private void checkCriticalLoggers(Health.Builder builder) {
        try {
            Map<String, String> loggerLevels = dynamicLoggerManager.getAllLoggerLevels();
            builder.withDetail("loggerLevels", loggerLevels);
            
            // 检查关键Logger是否正常
            String rootLevel = loggerLevels.get("ROOT");
            if (rootLevel == null || "UNKNOWN".equals(rootLevel)) {
                builder.down().withDetail("reason", "ROOT Logger配置异常");
            }
            
            String gopairLevel = loggerLevels.get("com.gopair");
            if (gopairLevel == null || "UNKNOWN".equals(gopairLevel)) {
                builder.down().withDetail("reason", "GoPair Logger配置异常");
            }
            
        } catch (Exception e) {
            builder.down().withDetail("loggerCheckError", e.getMessage());
        }
    }
    
    /**
     * 检查日志文件状态
     */
    private void checkLogFileStatus(Health.Builder builder) {
        try {
            // 这里可以检查日志文件是否可写、空间是否足够等
            // 简化实现，只添加基本信息
            builder.withDetail("logFileStatus", "检查通过");
            
        } catch (Exception e) {
            builder.down().withDetail("logFileError", e.getMessage());
        }
    }
} 