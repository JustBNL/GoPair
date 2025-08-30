package com.gopair.framework.logging.ops;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.cloud.context.environment.EnvironmentChangeEvent;
import org.springframework.cloud.context.scope.refresh.RefreshScopeRefreshedEvent;
import org.springframework.context.event.EventListener;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 动态日志管理器
 * 
 * 监听配置变更事件，动态调整Logger级别
 * 注意：通过 LoggingConfiguration 的 @Bean 方式创建，不使用 @Component
 * 
 * @author gopair
 */
@Slf4j
public class DynamicLoggerManager {
    
    private final LoggingSystem loggingSystem;
    
    /**
     * 记录最后的日志级别配置，用于比较变更
     */
    private final Map<String, String> lastLoggerLevels = new ConcurrentHashMap<>();
    
    public DynamicLoggerManager(LoggingSystem loggingSystem) {
        this.loggingSystem = loggingSystem;
        log.info("动态日志管理器初始化完成");
    }
    
    /**
     * 监听RefreshScope刷新事件
     */
    @EventListener
    public void handleRefreshScopeRefresh(RefreshScopeRefreshedEvent event) {
        log.info("检测到RefreshScope刷新事件，开始刷新日志配置");
        refreshLoggerLevels();
    }
    
    /**
     * 监听环境变量变更事件
     */
    @EventListener
    public void handleEnvironmentChange(EnvironmentChangeEvent event) {
        log.info("检测到环境变量变更事件，变更的键: {}", event.getKeys());
        
        // 检查是否有日志相关的配置变更
        boolean hasLoggingChange = event.getKeys().stream()
                .anyMatch(key -> key.startsWith("logging.level.") || 
                                key.startsWith("gopair.logging."));
        
        if (hasLoggingChange) {
            log.info("检测到日志相关配置变更，开始刷新日志配置");
            refreshLoggerLevels();
        }
    }
    
    /**
     * 刷新Logger级别
     */
    private void refreshLoggerLevels() {
        try {
            // 这里可以从Environment中读取新的日志配置
            // 由于我们使用的是Spring Boot的标准日志配置机制
            // LoggingSystem会自动处理大部分变更
            
            log.info("日志配置刷新完成");
            
            // 记录刷新事件用于监控
            recordRefreshEvent();
            
        } catch (Exception e) {
            log.error("刷新日志配置失败", e);
        }
    }
    
    /**
     * 动态设置Logger级别
     * 
     * @param loggerName Logger名称
     * @param level 日志级别
     */
    public void setLogLevel(String loggerName, String level) {
        try {
            LogLevel logLevel = LogLevel.valueOf(level.toUpperCase());
            loggingSystem.setLogLevel(loggerName, logLevel);
            
            log.info("动态设置Logger级别成功 - Logger: {}, Level: {}", loggerName, level);
            
            // 记录变更
            lastLoggerLevels.put(loggerName, level);
            
        } catch (IllegalArgumentException e) {
            log.error("无效的日志级别: {}", level, e);
            throw new IllegalArgumentException("无效的日志级别: " + level);
        } catch (Exception e) {
            log.error("设置Logger级别失败 - Logger: {}, Level: {}", loggerName, level, e);
            throw new RuntimeException("设置Logger级别失败", e);
        }
    }
    
    /**
     * 获取Logger级别
     * 
     * @param loggerName Logger名称
     * @return 日志级别
     */
    public String getLogLevel(String loggerName) {
        try {
            LogLevel logLevel = loggingSystem.getLoggerConfiguration(loggerName).getEffectiveLevel();
            return logLevel != null ? logLevel.name() : "INHERITED";
        } catch (Exception e) {
            log.warn("获取Logger级别失败 - Logger: {}", loggerName, e);
            return "UNKNOWN";
        }
    }
    
    /**
     * 获取所有Logger配置信息
     * 
     * @return Logger配置映射
     */
    public Map<String, String> getAllLoggerLevels() {
        Map<String, String> loggerLevels = new ConcurrentHashMap<>();
        
        try {
            // 获取常用的Logger配置
            String[] commonLoggers = {
                "ROOT",
                "com.gopair",
                "com.gopair.gateway",
                "com.gopair.userservice", 
                "com.gopair.roomservice",
                "com.gopair.messageservice",
                "com.gopair.filerelayservice",
                "com.gopair.voiceservice",
                "org.springframework.web",
                "org.springframework.security",
                "org.springframework.cloud.gateway",
                "com.baomidou.mybatisplus"
            };
            
            for (String loggerName : commonLoggers) {
                String level = getLogLevel(loggerName);
                loggerLevels.put(loggerName, level);
            }
            
        } catch (Exception e) {
            log.error("获取Logger级别列表失败", e);
        }
        
        return loggerLevels;
    }
    
    /**
     * 重置Logger级别为默认值
     * 
     * @param loggerName Logger名称
     */
    public void resetLogLevel(String loggerName) {
        try {
            loggingSystem.setLogLevel(loggerName, null);
            log.info("重置Logger级别成功 - Logger: {}", loggerName);
            
            lastLoggerLevels.remove(loggerName);
            
        } catch (Exception e) {
            log.error("重置Logger级别失败 - Logger: {}", loggerName, e);
            throw new RuntimeException("重置Logger级别失败", e);
        }
    }
    
    /**
     * 记录刷新事件
     */
    private void recordRefreshEvent() {
        // 这里可以发送指标到监控系统
        log.debug("记录日志配置刷新事件");
    }
    
    /**
     * 检查Logger是否存在
     * 
     * @param loggerName Logger名称
     * @return 是否存在
     */
    public boolean isLoggerExists(String loggerName) {
        try {
            return loggingSystem.getLoggerConfiguration(loggerName) != null;
        } catch (Exception e) {
            return false;
        }
    }
} 