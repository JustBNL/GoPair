package com.gopair.adminservice.filter;

import com.gopair.adminservice.context.AdminContext;
import com.gopair.adminservice.context.AdminContextHolder;
import com.gopair.adminservice.util.AdminJwtUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 管理员认证过滤器，拦截所有 /admin/** 请求并验证 JWT Token。
 *
 * 此 Filter 通过 SecurityConfig.addFilterBefore() 注册到 Spring Security 内部 Filter 链中，
 * 在 AuthorizationFilter 之前执行，设置 Authentication 到 SecurityContextHolder。
 *
 * 注意：无需 @Order 注解，其在 Spring Security Filter 链中的位置由 addFilterBefore() 决定。
 */
@Slf4j
@Component
public class AdminAuthFilter extends OncePerRequestFilter {

    @Value("${gopair.admin.jwt.secret}")
    private String jwtSecret;

    private static final String ADMIN_TOKEN_COOKIE = "admin_token";
    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String token = extractToken(request);
            if (token != null && AdminJwtUtils.validateToken(token, jwtSecret)) {
                String username = AdminJwtUtils.getUsernameFromToken(token, jwtSecret);
                String adminId = AdminJwtUtils.getAdminIdFromToken(token, jwtSecret);
                AdminContext context = new AdminContext(Long.parseLong(adminId), username);
                AdminContextHolder.set(context);
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                username,
                                null,
                                java.util.List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
                        );
                SecurityContextHolder.getContext().setAuthentication(authentication);
                filterChain.doFilter(request, response);
            } else {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":401,\"msg\":\"未登录或登录已过期\"}");
            }
        } finally {
            AdminContextHolder.clear();
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.equals("/admin/auth/login")
                || path.startsWith("/doc.html")
                || path.startsWith("/swagger-resources")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/webjars/")
                || path.equals("/favicon.ico");
    }

    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader(AUTH_HEADER);
        if (bearerToken != null && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(BEARER_PREFIX.length());
        }
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (ADMIN_TOKEN_COOKIE.equals(cookie.getName())) {
                    String value = cookie.getValue();
                    if (value != null && !value.isEmpty()) {
                        return value;
                    }
                }
            }
        }
        return null;
    }
}
