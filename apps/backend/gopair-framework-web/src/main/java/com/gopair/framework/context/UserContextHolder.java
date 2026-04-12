package com.gopair.framework.context;

import com.gopair.common.constants.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.util.Optional;

/**
 * 用户上下文管理器（双轨制实现）
 *
 * 同时管理业务上下文（ThreadLocal）和日志上下文（MDC）
 * 确保两者保持同步，互不干扰
 *
 * @author gopair
 */
@Slf4j
public class UserContextHolder {

    /**
     * 业务上下文ThreadLocal存储
     */
    private static final ThreadLocal<UserContext> CONTEXT_HOLDER = new ThreadLocal<>();

    /**
     * MDC键常量 (使用 SystemConstants 统一管理)
     */
    private static final String MDC_USER_ID = SystemConstants.MDC_USER_ID;
    private static final String MDC_NICKNAME = SystemConstants.MDC_NICKNAME;

    /**
     * 设置用户上下文（双轨制同步设置）
     *
     * @param context 用户上下文
     */
    public static void setContext(UserContext context) {
        // 设置业务上下文
        CONTEXT_HOLDER.set(context);

        // 同步设置日志上下文
        if (context != null) {
            MDC.put(MDC_USER_ID, context.getUserId() != null ? String.valueOf(context.getUserId()) : "");
            MDC.put(MDC_NICKNAME, context.getNickname() != null ? context.getNickname() : "");
        } else {
            // 清空MDC
            clearMDC();
        }
    }

    /**
     * 获取用户上下文
     *
     * @return 用户上下文的Optional包装
     */
    public static Optional<UserContext> getContext() {
        return Optional.ofNullable(CONTEXT_HOLDER.get());
    }

    /**
     * 获取当前用户ID（类型安全）
     *
     * @return 用户ID，可能为null
     */
    public static Long getCurrentUserId() {
        return getContext()
                .map(UserContext::getUserId)
                .orElse(null);
    }

    /**
     * 获取当前用户昵称（类型安全）
     *
     * @return 用户昵称，可能为null
     */
    public static String getCurrentNickname() {
        return getContext()
                .map(UserContext::getNickname)
                .orElse(null);
    }



    /**
     * 判断当前是否有用户上下文
     *
     * @return 是否存在用户上下文
     */
    public static boolean hasContext() {
        return CONTEXT_HOLDER.get() != null;
    }

    /**
     * 清理上下文（双轨制同步清理）
     *
     * 请求结束时必须调用此方法，防止内存泄漏
     */
    public static void clear() {
        try {
            // 清理业务上下文
            CONTEXT_HOLDER.remove();

            // 清理日志上下文
            clearMDC();
        } catch (Exception e) {
            log.warn("清理用户上下文时发生异常", e);
        }
    }

    /**
     * 清理MDC上下文
     */
    private static void clearMDC() {
        MDC.remove(MDC_USER_ID);
        MDC.remove(MDC_NICKNAME);
    }

    /**
     * 更新用户ID（用于登录后更新）
     *
     * @param userId 新的用户ID
     */
    public static void updateUserId(Long userId) {
        UserContext context = CONTEXT_HOLDER.get();
        if (context != null) {
            context.setUserId(userId);
            MDC.put(MDC_USER_ID, userId != null ? String.valueOf(userId) : "");
        }
    }

    /**
     * 更新用户昵称（用于登录后更新）
     *
     * @param nickname 新的用户昵称
     */
    public static void updateNickname(String nickname) {
        UserContext context = CONTEXT_HOLDER.get();
        if (context != null) {
            context.setNickname(nickname);
            MDC.put(MDC_NICKNAME, nickname != null ? nickname : "");
        }
    }
}
