package com.gopair.framework.logging.ops;

import com.gopair.framework.logging.event.ExceptionLogEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 日志指标收集器
 *
 * 收集日志相关的指标数据，为监控系统提供数据
 *
 * @author gopair
 */
@Slf4j
public class LogMetricsCollector {

    private final AtomicLong logRecordCount = new AtomicLong(0);
    private final AtomicLong exceptionLogCount = new AtomicLong(0);
    private final AtomicLong configRefreshCount = new AtomicLong(0);

    public void recordLog() {
        logRecordCount.incrementAndGet();
    }

    public void recordExceptionLog() {
        exceptionLogCount.incrementAndGet();
    }

    public void recordConfigRefresh() {
        configRefreshCount.incrementAndGet();
        log.debug("[日志指标] 记录配置刷新指标，当前计数: {}", configRefreshCount.get());
    }

    @EventListener
    public void onExceptionLog(ExceptionLogEvent event) {
        recordExceptionLog();
    }

    public long getLogRecordCount() {
        return logRecordCount.get();
    }

    public long getExceptionLogCount() {
        return exceptionLogCount.get();
    }

    public long getConfigRefreshCount() {
        return configRefreshCount.get();
    }

    public void resetAll() {
        logRecordCount.set(0);
        exceptionLogCount.set(0);
        configRefreshCount.set(0);
        log.info("[日志指标] 所有日志指标计数器已重置");
    }
} 