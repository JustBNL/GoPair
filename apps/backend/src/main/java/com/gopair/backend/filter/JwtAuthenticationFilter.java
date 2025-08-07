package com.gopair.backend.filter;

import com.gopair.backend.common.constants.MessageConstants;
import com.gopair.backend.common.util.JwtUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT认证过滤器
 * 
 * 继承OncePerRequestFilter，确保每个请求只执行一次过滤
 * 用于从请求头中提取JWT令牌并验证其有效性
 * 
 * @author gopair
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Autowired
    private UserDetailsService userDetailsService;

    @Autowired
    private JwtUtils jwtUtils;

    /**
     * 过滤器核心方法，处理每个HTTP请求
     * 
     * @param request HTTP请求
     * @param response HTTP响应
     * @param filterChain 过滤器链
     * @throws ServletException Servlet异常
     * @throws IOException IO异常
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        // 从请求头中获取Authorization
        final String authHeader = request.getHeader("Authorization");
        
        String username = null;
        String jwt = null;

        // 检查Authorization头是否存在且以"Bearer "开头
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            // 提取JWT令牌（去掉"Bearer "前缀）
            jwt = authHeader.substring(7);
            try {
                // 从JWT中提取用户名
                username = jwtUtils.getUsernameFromToken(jwt);
            } catch (Exception e) {
                logger.error(MessageConstants.TOKEN_INVALID, e);
            }
        }

        // 如果成功提取了用户名且当前没有认证信息
        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            // 加载用户详情
            UserDetails userDetails = this.userDetailsService.loadUserByUsername(username);

            // 验证JWT令牌
            if (jwtUtils.validateToken(jwt, userDetails)) {
                // 创建认证令牌
                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());
                
                // 设置认证详情
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                
                // 将认证信息设置到SecurityContext中
                SecurityContextHolder.getContext().setAuthentication(authToken);
                
                logger.info("用户 [" + username + "] 认证成功");
            } else {
                logger.warn(MessageConstants.TOKEN_INVALID);
            }
        }

        // 继续执行过滤器链
        filterChain.doFilter(request, response);
    }
} 