package com.gopair.framework.logging.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 通用日志记录注解
 *
 * 用于标记需要记录操作的方法，并可选择记录性能
 *
 * @author gopair
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface LogRecord {

    /**
     * 操作描述
     */
    String operation();

    /**
     * 所属模块
     */
    String module() default "";

    /**
     * 是否记录参数
     */
    boolean includeArgs() default true;

    /**
     * 是否记录返回值
     */
    boolean includeResult() default false;

    /**
     * 是否记录性能耗时
     */
    boolean logPerformance() default true;

} 