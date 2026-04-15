package com.gopair.adminservice.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 管理员操作审计注解，标注此注解的方法会自动记录审计日志
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AdminAudit {

    /**
     * 操作类型，如 USER_DISABLE、USER_ENABLE、ROOM_CLOSE、FILE_DELETE
     */
    String operation();

    /**
     * 目标类型，如 USER、ROOM、MESSAGE、FILE
     */
    String targetType();
}
