package com.gopair.framework.logging.ops;

import com.gopair.framework.logging.event.LogRecordEvent;
import com.gopair.framework.logging.event.ExceptionLogEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 日志指标收集器
 * 
 * 收集日志相关的指标数据，为监控系统提供数据
 * 注意：通过 LoggingConfiguration 的 @Bean 方式创建，不使用 @Component
 * 
 * @author gopair
 */
@Slf4j
public class LogMetricsCollector {
    
    // 日志计数器
    private final AtomicLong logRecordCount = new AtomicLong(0);
    private final AtomicLong exceptionLogCount = new AtomicLong(0);
    private final AtomicLong configRefreshCount = new AtomicLong(0);
    
    /**
     * 记录一次日志记录
     */
    public void recordLog() {
        logRecordCount.incrementAndGet();
    }
    
    /**
     * 记录异常日志指标
     */
    public void recordExceptionLog() {
        exceptionLogCount.incrementAndGet();
    }
    
    /**
     * 记录配置刷新指标
     */
    public void recordConfigRefresh() {
        configRefreshCount.incrementAndGet();
        log.debug("记录配置刷新指标，当前计数: {}", configRefreshCount.get());
    }
    
    // 事件监听（解耦）
    @EventListener
    public void onLogRecord(LogRecordEvent event) {
        recordLog();
    }
    
    @EventListener
    public void onExceptionLog(ExceptionLogEvent event) {
        recordExceptionLog();
    }
    
    /**
     * 获取日志记录总数
     * @return 日志记录总数
     */
    public long getLogRecordCount() {
        return logRecordCount.get();
    }
    
    /**
     * 获取异常日志计数
     */
    public long getExceptionLogCount() {
        return exceptionLogCount.get();
    }
    
    /**
     * 获取配置刷新计数
     */
    public long getConfigRefreshCount() {
        return configRefreshCount.get();
    }
    
    /**
     * 重置所有计数器
     */
    public void resetAll() {
        logRecordCount.set(0);
        exceptionLogCount.set(0);
        configRefreshCount.set(0);
        log.info("所有日志指标计数器已重置");
    }
} 