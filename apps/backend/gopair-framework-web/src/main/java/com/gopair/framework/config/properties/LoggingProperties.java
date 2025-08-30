package com.gopair.framework.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 日志功能属性配置
 * 
 * 统一管理所有日志相关的配置参数，合并原 LoggingOpsProperties 和 LoggingAopProperties
 * 
 * @author gopair
 */
@Data
@ConfigurationProperties(prefix = "gopair.logging")
public class LoggingProperties {
    
    /**
     * 是否启用日志功能
     */
    private boolean enabled = true;
    
    /**
     * AOP日志配置
     */
    private Aop aop = new Aop();
    
    /**
     * 日志运维配置
     */
    private Ops ops = new Ops();
    
    @Data
    public static class Aop {
        
        /**
         * 是否启用AOP日志
         */
        private boolean enabled = true;
        
        /**
         * 是否记录方法参数
         */
        private boolean logArgs = true;
        
        /**
         * 是否记录方法返回值
         */
        private boolean logResult = true;
        
        /**
         * 是否记录方法执行时间
         */
        private boolean logExecutionTime = true;
    }
    
    @Data
    public static class Ops {
        
        /**
         * 是否启用日志运维能力
         */
        private boolean enabled = true;
        
        /**
         * 是否启用 MVC 控制器
         */
        private boolean mvcEnabled = true;
        
        /**
         * 是否启用 Actuator 端点
         */
        private boolean actuatorEnabled = true;
        
        /**
         * 是否启用健康检查
         */
        private boolean healthEnabled = true;
        
        /**
         * 是否启用指标收集
         */
        private boolean metricsEnabled = true;
    }
} 