package com.gopair.framework.context;

import com.gopair.common.constants.MessageConstants;
import com.gopair.framework.config.properties.ContextProperties;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.UUID;

/**
 * 上下文初始化过滤器
 * 
 * 统一处理请求上下文的初始化和清理
 * 实现双轨制上下文管理（UserContextHolder + MDC）
 * 
 * @author gopair
 */
@Slf4j
@Order(Integer.MIN_VALUE + 100)
public class ContextInitFilter implements Filter {
    
    private final ContextProperties properties;
    private final AntPathMatcher pathMatcher;
    
    public ContextInitFilter(ContextProperties properties) {
        this.properties = properties;
        this.pathMatcher = new AntPathMatcher();
    }
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) 
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        
        // 检查是否需要排除此路径
        String requestURI = httpRequest.getRequestURI();
        if (isExcludedPath(requestURI)) {
            log.debug("[上下文管理] 跳过排除路径: {}", requestURI);
            chain.doFilter(request, response);
            return;
        }
        
        try {
            // 初始化上下文
            initializeContext(httpRequest);
            
            // 继续过滤器链
            chain.doFilter(request, response);
            
        } finally {
            // 清理上下文
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
     * 
     * @param request HTTP请求
     */
    private void initializeContext(HttpServletRequest request) {
        try {
            // 生成或提取TraceId
            String traceId = generateOrExtractTraceId(request);
            
            // 额外设置traceId到MDC（用于tracing）
            MDC.put(MessageConstants.MDC_TRACE_ID, traceId);
            
            // 从请求中提取用户信息
            Long userId = extractUserId(request);
            String nickname = extractNickname(request);
            
            // 如果有用户信息，创建用户上下文
            if (userId != null || nickname != null) {
                UserContext userContext = UserContext.of(userId, nickname);
                UserContextHolder.setContext(userContext);
                
                log.debug("[上下文管理] 初始化请求上下文 - URI: {}, TraceId: {}, UserId: {}, Nickname: {}", 
                         request.getRequestURI(), traceId, userId, nickname);
            } else {
                log.debug("[上下文管理] 初始化请求上下文 - URI: {}, TraceId: {} (无用户信息)", 
                         request.getRequestURI(), traceId);
            }
                     
        } catch (Exception e) {
            log.warn("[上下文管理] 初始化上下文失败", e);
            // 即使初始化失败，也要确保有基本的traceId
            String fallbackTraceId = UUID.randomUUID().toString().replace("-", "");
            MDC.put(MessageConstants.MDC_TRACE_ID, fallbackTraceId);
        }
    }
    
    /**
     * 生成或提取TraceId
     * 
     * @param request HTTP请求
     * @return TraceId
     */
    private String generateOrExtractTraceId(HttpServletRequest request) {
        // 首先尝试从请求头获取
        String traceId = request.getHeader(MessageConstants.HEADER_TRACE_ID);
        
        if (traceId == null || traceId.trim().isEmpty()) {
            // 生成新的TraceId
            traceId = UUID.randomUUID().toString().replace("-", "");
        }
        
        return traceId;
    }
    
    /**
     * 从请求中提取用户ID
     * 
     * @param request HTTP请求
     * @return 用户ID，可能为null
     */
    private Long extractUserId(HttpServletRequest request) {
        try {
            String userIdHeader = request.getHeader(MessageConstants.HEADER_USER_ID);
            if (userIdHeader != null && !userIdHeader.isEmpty()) {
                try {
                    return Long.parseLong(userIdHeader);
                } catch (NumberFormatException e) {
                    log.debug("[上下文管理] 无效的用户ID格式: {}", userIdHeader);
                }
            }
        } catch (Exception e) {
            log.debug("[上下文管理] 提取用户ID失败", e);
        }
        return null;
    }
    
    /**
     * 从请求中提取用户昵称
     * 
     * @param request HTTP请求
     * @return 用户昵称，可能为null
     */
    private String extractNickname(HttpServletRequest request) {
        try {
            String nicknameHeader = request.getHeader(MessageConstants.HEADER_NICKNAME);
            if (nicknameHeader != null && !nicknameHeader.isEmpty()) {
                return nicknameHeader;
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
            // 双轨制清理
            UserContextHolder.clear();
            
            log.debug("[上下文管理] 请求上下文清理完成");
            
        } catch (Exception e) {
            log.warn("[上下文管理] 清理上下文失败", e);
        }
    }
    
    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        log.info("[上下文管理] ContextInitFilter 初始化完成");
    }
    
    @Override
    public void destroy() {
        log.info("[上下文管理] ContextInitFilter 销毁");
    }
} 