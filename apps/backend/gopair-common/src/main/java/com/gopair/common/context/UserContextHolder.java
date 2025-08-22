package com.gopair.common.context;

import lombok.extern.slf4j.Slf4j;

/**
 * 用户上下文持有者
 * 
 * 使用ThreadLocal存储和管理用户上下文信息，确保在整个请求生命周期中可用
 * 
 * @author gopair
 */
@Slf4j
public class UserContextHolder {

    /**
     * ThreadLocal变量，存储用户上下文
     */
    private static final ThreadLocal<UserContext> context = new ThreadLocal<>();

    /**
     * 设置用户上下文
     * 
     * @param userContext 用户上下文对象
     */
    public static void setContext(UserContext userContext) {
        if (userContext != null) {
            context.set(userContext);
            log.debug("设置用户上下文: userId={}, username={}, requestId={}", 
                     userContext.getUserId(), userContext.getUsername(), userContext.getRequestId());
        }
    }

    /**
     * 获取用户上下文
     * 
     * @return 用户上下文对象，如果未设置则返回null
     */
    public static UserContext getContext() {
        return context.get();
    }

    /**
     * 获取当前用户ID
     * 
     * @return 用户ID，如果用户上下文未设置或无效则返回null
     */
    public static Long getCurrentUserId() {
        UserContext ctx = getContext();
        return ctx != null ? ctx.getUserId() : null;
    }

    /**
     * 获取当前用户名
     * 
     * @return 用户名，如果用户上下文未设置则返回null
     */
    public static String getCurrentUsername() {
        UserContext ctx = getContext();
        return ctx != null ? ctx.getUsername() : null;
    }

    /**
     * 获取当前请求ID
     * 
     * @return 请求ID，如果用户上下文未设置则返回null
     */
    public static String getCurrentRequestId() {
        UserContext ctx = getContext();
        return ctx != null ? ctx.getRequestId() : null;
    }

    /**
     * 检查当前是否有有效的用户上下文
     * 
     * @return 如果有有效的用户上下文则返回true
     */
    public static boolean hasValidContext() {
        UserContext ctx = getContext();
        return ctx != null && ctx.isValid();
    }

    /**
     * 清理用户上下文
     * 
     * 必须在请求结束时调用，防止内存泄漏和线程复用时的数据污染
     */
    public static void clear() {
        UserContext ctx = context.get();
        if (ctx != null) {
            log.debug("清理用户上下文: requestId={}", ctx.getRequestId());
        }
        context.remove();
    }
} 