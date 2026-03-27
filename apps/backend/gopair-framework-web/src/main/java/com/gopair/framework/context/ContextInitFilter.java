package com.gopair.framework.context;

import com.gopair.common.constants.MessageConstants;
import com.gopair.framework.config.properties.ContextProperties;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.lang.Nullable;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.UUID;

/**
 * 上下文初始化过滤器
 *
 * 统一处理请求上下文的初始化和清理，实现双轨制上下文管理（UserContextHolder + MDC）。
 *
 * traceId 优先级：
 *   1. Brave Tracer 当前 Span（通过 TraceContextSupport，需引入 micrometer-tracing-bridge-brave）
 *   2. 请求头 X-Trace-Id（上游手动传入）
 *   3. 本地生成 UUID（兜底 fallback）
 *
 * userId/nickname 优先级：
 *   1. 从请求头 X-User-Id / X-Nickname 提取（网关注入）
 *   2. 若 TraceContextSupport 可用，同步写入 Brave BaggageField（供 Zipkin 关联）
 *   3. 无论如何都写入 MDC（保证日志格式输出正确）
 *
 * @author gopair
 */
@Slf4j
@Order(Integer.MIN_VALUE + 100)
public class ContextInitFilter implements Filter {

    private final ContextProperties properties;
    private final AntPathMatcher pathMatcher;

    /**
     * 可选注入：仅在引入 micrometer-tracing-bridge-brave 时存在
     * 通过 ContextConfiguration 条件注入，本 Filter 不直接依赖 Brave API
     */
    @Nullable
    private final TraceContextSupport traceContextSupport;

    public ContextInitFilter(ContextProperties properties,
                             @Nullable TraceContextSupport traceContextSupport) {
        this.properties = properties;
        this.traceContextSupport = traceContextSupport;
        this.pathMatcher = new AntPathMatcher();
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String requestURI = httpRequest.getRequestURI();

        if (isExcludedPath(requestURI)) {
            log.debug("[上下文管理] 跳过排除路径: {}", requestURI);
            chain.doFilter(request, response);
            return;
        }

        try {
            initializeContext(httpRequest);
            chain.doFilter(request, response);
        } finally {
            cleanupContext();
        }
    }

    /**
     * 检查是否为排除路径
     */
    private boolean isExcludedPath(String requestURI) {
        if (properties.getUser().getExcludedPaths() == null) {
            return false;
        }
        for (String excludedPath : properties.getUser().getExcludedPaths()) {
            if (pathMatcher.match(excludedPath, requestURI)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 初始化请求上下文
     */
    private void initializeContext(HttpServletRequest request) {
        try {
            // === Step 1: 确定 traceId ===
            String traceId = resolveTraceId(request);
            // 仅当 MDC 中没有 Brave 已写入的 traceId 时才手动写入
            // （Brave 自动配置会在 Filter 链更早的位置写入 traceId，这里做补充）
            if (!StringUtils.hasText(MDC.get(MessageConstants.MDC_TRACE_ID))) {
                MDC.put(MessageConstants.MDC_TRACE_ID, traceId);
            }

            // === Step 2: 提取用户信息 ===
            String userIdStr = extractUserIdStr(request);
            String nickname = extractNickname(request);

            // === Step 3: 写入用户上下文 ===
            if (StringUtils.hasText(userIdStr) || StringUtils.hasText(nickname)) {
                // 写入 UserContextHolder（业务代码使用）
                Long userId = parseUserId(userIdStr);
                UserContext userContext = UserContext.of(userId, nickname);
                UserContextHolder.setContext(userContext);

                // 写入 MDC（日志格式输出）+ 可选写入 Brave BaggageField（Zipkin 关联）
                if (traceContextSupport != null) {
                    // TraceContextSupport 内部同时写 BaggageField 和 MDC
                    traceContextSupport.enrichMdcWithUserBaggage(userIdStr, nickname);
                } else {
                    // 降级：纯 MDC 写入
                    if (StringUtils.hasText(userIdStr)) {
                        MDC.put(MessageConstants.MDC_USER_ID, userIdStr);
                    }
                    if (StringUtils.hasText(nickname)) {
                        MDC.put(MessageConstants.MDC_NICKNAME, nickname);
                    }
                }

                log.debug("[上下文管理] 初始化请求上下文 - URI: {}, TraceId: {}, UserId: {}, Nickname: {}",
                        request.getRequestURI(), MDC.get(MessageConstants.MDC_TRACE_ID), userIdStr, nickname);
            } else {
                log.debug("[上下文管理] 初始化请求上下文 - URI: {}, TraceId: {} (无用户信息)",
                        request.getRequestURI(), MDC.get(MessageConstants.MDC_TRACE_ID));
            }

        } catch (Exception e) {
            log.warn("[上下文管理] 初始化上下文失败", e);
            // 兜底：确保始终有 traceId
            if (!StringUtils.hasText(MDC.get(MessageConstants.MDC_TRACE_ID))) {
                MDC.put(MessageConstants.MDC_TRACE_ID, UUID.randomUUID().toString().replace("-", ""));
            }
        }
    }

    /**
     * 解析 traceId：优先使用 Brave，其次请求头，最后本地生成
     */
    private String resolveTraceId(HttpServletRequest request) {
        // 优先级 1：Brave Tracer 当前 Span
        if (traceContextSupport != null) {
            String braveTraceId = traceContextSupport.getCurrentTraceId();
            if (StringUtils.hasText(braveTraceId)) {
                log.debug("[上下文管理] 使用 Brave traceId: {}", braveTraceId);
                return braveTraceId;
            }
        }
        // 优先级 2：请求头 X-Trace-Id
        String headerTraceId = request.getHeader(MessageConstants.HEADER_TRACE_ID);
        if (StringUtils.hasText(headerTraceId)) {
            log.debug("[上下文管理] 使用请求头 traceId: {}", headerTraceId);
            return headerTraceId;
        }
        // 优先级 3：本地生成 UUID
        String generated = UUID.randomUUID().toString().replace("-", "");
        log.debug("[上下文管理] 本地生成 traceId: {}", generated);
        return generated;
    }

    /**
     * 从请求头提取用户 ID 字符串（保留字符串形式供 BaggageField 使用）
     */
    private String extractUserIdStr(HttpServletRequest request) {
        try {
            String userIdHeader = request.getHeader(MessageConstants.HEADER_USER_ID);
            if (StringUtils.hasText(userIdHeader)) {
                return userIdHeader.trim();
            }
        } catch (Exception e) {
            log.debug("[上下文管理] 提取用户ID失败", e);
        }
        return null;
    }

    /**
     * 解析用户 ID 字符串为 Long
     */
    private Long parseUserId(String userIdStr) {
        if (!StringUtils.hasText(userIdStr)) {
            return null;
        }
        try {
            return Long.parseLong(userIdStr);
        } catch (NumberFormatException e) {
            log.debug("[上下文管理] 无效的用户ID格式: {}", userIdStr);
            return null;
        }
    }

    /**
     * 从请求头提取用户昵称
     */
    private String extractNickname(HttpServletRequest request) {
        try {
            String nicknameHeader = request.getHeader(MessageConstants.HEADER_NICKNAME);
            if (StringUtils.hasText(nicknameHeader)) {
                return nicknameHeader.trim();
            }
        } catch (Exception e) {
            log.debug("[上下文管理] 提取用户昵称失败", e);
        }
        return null;
    }

    /**
     * 清理请求上下文
     */
    private void cleanupContext() {
        try {
            UserContextHolder.clear();
            MDC.remove(MessageConstants.MDC_USER_ID);
            MDC.remove(MessageConstants.MDC_NICKNAME);
            // 注意：MDC_TRACE_ID 由 Brave 自动管理，此处不强制移除，避免影响其他 Span
            log.debug("[上下文管理] 请求上下文清理完成");
        } catch (Exception e) {
            log.warn("[上下文管理] 清理上下文失败", e);
        }
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        log.info("[上下文管理] ContextInitFilter 初始化完成，TraceContextSupport={}",
                traceContextSupport != null ? "已启用(Brave模式)" : "未启用(MDC降级模式)");
    }

    @Override
    public void destroy() {
        log.info("[上下文管理] ContextInitFilter 销毁");
    }
}
