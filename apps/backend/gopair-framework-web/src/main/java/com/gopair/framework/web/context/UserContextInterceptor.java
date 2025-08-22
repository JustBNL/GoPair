package com.gopair.framework.web.context;

import com.gopair.common.context.UserContext;
import com.gopair.common.context.UserContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 用户上下文拦截器
 * 
 * 在请求处理前从HTTP头中提取用户信息并设置到ThreadLocal中，
 * 在请求完成后清理ThreadLocal防止内存泄漏
 * 
 * @author gopair
 */
@Slf4j
public class UserContextInterceptor implements HandlerInterceptor {

    /**
     * 用户ID头名称
     */
    private static final String USER_ID_HEADER = "X-User-Id";
    
    /**
     * 用户名头名称
     */
    private static final String USERNAME_HEADER = "X-Username";

    /**
     * 请求处理前的预处理
     * 
     * 从HTTP头中提取用户信息并设置到ThreadLocal中
     * 
     * @param request HTTP请求
     * @param response HTTP响应
     * @param handler 处理器
     * @return 是否继续处理请求
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        try {
            // 从HTTP头中提取用户信息
            String userIdStr = request.getHeader(USER_ID_HEADER);
            String username = request.getHeader(USERNAME_HEADER);
            
            if (StringUtils.hasText(userIdStr)) {
                try {
                    Long userId = Long.parseLong(userIdStr);
                    
                    // 创建用户上下文并设置到ThreadLocal
                    UserContext userContext = UserContext.of(userId, username);
                    UserContextHolder.setContext(userContext);
                    
                    log.debug("用户上下文设置成功: userId={}, username={}, path={}", 
                             userId, username, request.getRequestURI());
                             
                } catch (NumberFormatException e) {
                    log.warn("无效的用户ID格式: {}, path={}", userIdStr, request.getRequestURI());
                }
            } else {
                log.debug("请求中未包含用户信息: path={}", request.getRequestURI());
            }
            
        } catch (Exception e) {
            log.error("设置用户上下文时发生异常: path={}", request.getRequestURI(), e);
        }
        
        return true;
    }

    /**
     * 请求完成后的清理处理
     * 
     * 清理ThreadLocal中的用户上下文，防止内存泄漏
     * 
     * @param request HTTP请求
     * @param response HTTP响应
     * @param handler 处理器
     * @param ex 异常（如果有）
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, 
                               Object handler, Exception ex) {
        try {
            // 清理ThreadLocal
            UserContextHolder.clear();
            log.debug("用户上下文清理完成: path={}", request.getRequestURI());
            
        } catch (Exception e) {
            log.error("清理用户上下文时发生异常: path={}", request.getRequestURI(), e);
        }
    }
} 