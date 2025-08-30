package com.gopair.framework.logging.event;

import org.springframework.context.ApplicationEvent;

/**
 * 异常日志事件
 * 
 * @author gopair
 */
public class ExceptionLogEvent extends ApplicationEvent {

    public ExceptionLogEvent(Object source) {
        super(source);
    }
} 