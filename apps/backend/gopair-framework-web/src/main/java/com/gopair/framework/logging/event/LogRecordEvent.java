package com.gopair.framework.logging.event;

import org.springframework.context.ApplicationEvent;

/**
 * 日志记录事件
 * 
 * @author gopair
 */
public class LogRecordEvent extends ApplicationEvent {
    public LogRecordEvent(Object source) {
        super(source);
    }
} 