package com.gopair.adminservice.context;

/**
 * 管理员上下文持有器，管理当前请求的管理员身份信息
 *
 * @author gopair
 */
public class AdminContextHolder {

    private static final ThreadLocal<AdminContext> CONTEXT = new ThreadLocal<>();

    public static void set(AdminContext context) {
        CONTEXT.set(context);
    }

    public static AdminContext get() {
        return CONTEXT.get();
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
