package com.gopair.framework.config;

import com.gopair.framework.config.properties.LoggingProperties;
import com.gopair.framework.logging.ops.DynamicLoggerManager;
import com.gopair.framework.logging.ops.LogLevelController;
import com.gopair.framework.logging.ops.LogMetricsCollector;
import com.gopair.framework.logging.ops.LoggingEndpoint;
import com.gopair.framework.logging.ops.LoggingHealthIndicator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.web.bind.annotation.RestController;

/**
 * 日志功能配置类
 * 
 * @author gopair
 */
@Slf4j
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "gopair.logging", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(LoggingProperties.class)
@EnableAspectJAutoProxy
@ComponentScan(basePackages = {
    "com.gopair.framework.logging.aspect"  // AOP切面扫描
})
public class LoggingConfiguration {

    // === AOP日志配置部分 ===
    
    public LoggingConfiguration() {
        log.info("[框架配置] 日志功能配置已启动");
    }

    // === 运维功能配置部分 ===

    @Bean
    @ConditionalOnClass(LoggingSystem.class)
    public DynamicLoggerManager dynamicLoggerManager(LoggingSystem loggingSystem) {
        return new DynamicLoggerManager(loggingSystem);
    }

    @Bean
    @ConditionalOnClass(RestController.class)
    @ConditionalOnExpression("${gopair.logging.enabled:true} && ${gopair.logging.ops.mvcEnabled:true}")
    @ConditionalOnBean(LogMetricsCollector.class)
    public LogLevelController logLevelController(DynamicLoggerManager dynamicLoggerManager,
                                                 LogMetricsCollector logMetricsCollector) {
        return new LogLevelController(dynamicLoggerManager, logMetricsCollector);
    }

    @Bean
    @ConditionalOnExpression("${gopair.logging.enabled:true} && ${gopair.logging.ops.metricsEnabled:true}")
    public LogMetricsCollector logMetricsCollector() {
        return new LogMetricsCollector();
    }

    @Bean
    @ConditionalOnClass(HealthIndicator.class)
    @ConditionalOnExpression("${gopair.logging.enabled:true} && ${gopair.logging.ops.healthEnabled:true}")
    @ConditionalOnBean(DynamicLoggerManager.class)
    public LoggingHealthIndicator loggingHealthIndicator(LoggingSystem loggingSystem,
                                                         DynamicLoggerManager dynamicLoggerManager) {
        return new LoggingHealthIndicator(loggingSystem, dynamicLoggerManager);
    }

    @Bean
    @ConditionalOnClass(Endpoint.class)
    @ConditionalOnExpression("${gopair.logging.enabled:true} && ${gopair.logging.ops.actuatorEnabled:true}")
    @ConditionalOnBean(LogMetricsCollector.class)
    public LoggingEndpoint loggingEndpoint(DynamicLoggerManager dynamicLoggerManager,
                                           LogMetricsCollector logMetricsCollector) {
        return new LoggingEndpoint(dynamicLoggerManager, logMetricsCollector);
    }
} 