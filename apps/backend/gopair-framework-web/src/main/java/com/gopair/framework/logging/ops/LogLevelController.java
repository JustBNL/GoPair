package com.gopair.framework.logging.ops;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 日志级别控制REST API
 * 
 * 提供动态调整日志级别的REST接口
 * 
 * @author gopair
 */
@Slf4j
@ResponseBody
@RequestMapping("/ops/logging")
public class LogLevelController {
    
    private final DynamicLoggerManager dynamicLoggerManager;
    private final LogMetricsCollector logMetricsCollector;
    
    public LogLevelController(DynamicLoggerManager dynamicLoggerManager,
                             LogMetricsCollector logMetricsCollector) {
        this.dynamicLoggerManager = dynamicLoggerManager;
        this.logMetricsCollector = logMetricsCollector;
    }
    
    /**
     * 获取所有Logger级别
     */
    @GetMapping("/levels")
    public ResponseEntity<Map<String, Object>> getAllLoggerLevels() {
        try {
            Map<String, String> loggerLevels = dynamicLoggerManager.getAllLoggerLevels();
            
            Map<String, Object> response = new HashMap<>();
            response.put("loggerLevels", loggerLevels);
            response.put("timestamp", System.currentTimeMillis());
            response.put("total", loggerLevels.size());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("获取Logger级别列表失败", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }
    
    /**
     * 获取指定Logger的级别
     */
    @GetMapping("/level/{loggerName}")
    public ResponseEntity<Map<String, Object>> getLogLevel(@PathVariable String loggerName) {
        try {
            String level = dynamicLoggerManager.getLogLevel(loggerName);
            boolean exists = dynamicLoggerManager.isLoggerExists(loggerName);
            
            Map<String, Object> response = new HashMap<>();
            response.put("loggerName", loggerName);
            response.put("level", level);
            response.put("exists", exists);
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("获取Logger级别失败 - Logger: {}", loggerName, e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }
    
    /**
     * 设置Logger级别
     */
    @PostMapping("/level/{loggerName}")
    public ResponseEntity<Map<String, Object>> setLogLevel(
            @PathVariable String loggerName,
            @RequestParam String level) {
        
        try {
            // 验证日志级别
            if (!isValidLogLevel(level)) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "无效的日志级别: " + level));
            }
            
            // 设置日志级别
            dynamicLoggerManager.setLogLevel(loggerName, level);
            
            Map<String, Object> response = new HashMap<>();
            response.put("loggerName", loggerName);
            response.put("level", level);
            response.put("success", true);
            response.put("timestamp", System.currentTimeMillis());
            response.put("message", "日志级别设置成功");
            
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            log.warn("无效的日志级别参数 - Logger: {}, Level: {}", loggerName, level);
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
                    
        } catch (Exception e) {
            log.error("设置Logger级别失败 - Logger: {}, Level: {}", loggerName, level, e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }
    
    /**
     * 重置Logger级别
     */
    @DeleteMapping("/level/{loggerName}")
    public ResponseEntity<Map<String, Object>> resetLogLevel(@PathVariable String loggerName) {
        try {
            dynamicLoggerManager.resetLogLevel(loggerName);
            
            Map<String, Object> response = new HashMap<>();
            response.put("loggerName", loggerName);
            response.put("success", true);
            response.put("timestamp", System.currentTimeMillis());
            response.put("message", "日志级别重置成功");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("重置Logger级别失败 - Logger: {}", loggerName, e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }
    
    /**
     * 获取日志指标
     */
    @GetMapping("/metrics")
    public ResponseEntity<Map<String, Object>> getLogMetrics() {
        try {
            Map<String, Object> metrics = new HashMap<>();
            metrics.put("logRecordCount", logMetricsCollector.getLogRecordCount());
            metrics.put("exceptionLogCount", logMetricsCollector.getExceptionLogCount());
            metrics.put("configRefreshCount", logMetricsCollector.getConfigRefreshCount());
            metrics.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(metrics);
            
        } catch (Exception e) {
            log.error("获取日志指标失败", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }
    
    /**
     * 重置日志指标
     */
    @PostMapping("/reset-metrics")
    public String resetMetrics() {
        logMetricsCollector.resetAll();
        return "Log metrics have been reset.";
    }
    
    /**
     * 验证日志级别是否有效
     */
    private boolean isValidLogLevel(String level) {
        if (level == null || level.trim().isEmpty()) {
            return false;
        }
        
        try {
            String upperLevel = level.toUpperCase();
            return "TRACE".equals(upperLevel) || 
                   "DEBUG".equals(upperLevel) || 
                   "INFO".equals(upperLevel) || 
                   "WARN".equals(upperLevel) || 
                   "ERROR".equals(upperLevel) ||
                   "OFF".equals(upperLevel);
        } catch (Exception e) {
            return false;
        }
    }
} 