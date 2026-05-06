package com.gopair.adminservice.filter;

import com.gopair.adminservice.context.AdminContext;
import com.gopair.adminservice.context.AdminContextHolder;
import com.gopair.adminservice.util.AdminJwtUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 管理员认证过滤器，拦截所有 /admin/** 请求并验证 JWT Token。
 * 由 SecurityConfig 作为 @Bean 创建，在 Spring Security 内部 Filter 链中
 * (addFilterBefore AnonymousAuthenticationFilter) 执行，设置 Authentication。
 */
public class AdminAuthFilter extends OncePerRequestFilter {

    private final String jwtSecret;

    public AdminAuthFilter(@Value("${gopair.admin.jwt.secret}") String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    private static final String ADMIN_TOKEN_COOKIE = "admin_token";
    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String PUBLIC_PATH_LOGIN = "/admin/auth/login";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (isPublicPath(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = extractToken(request);
        if (token == null || !AdminJwtUtils.validateToken(token, jwtSecret)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"msg\":\"未登录或登录已过期\"}");
            return;
        }
        String username = AdminJwtUtils.getUsernameFromToken(token, jwtSecret);
        String adminId = AdminJwtUtils.getAdminIdFromToken(token, jwtSecret);
        AdminContext context = new AdminContext(Long.parseLong(adminId), username);
        AdminContextHolder.set(context);
        try {
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            username,
                            null,
                            java.util.List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
                    );
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } finally {
            AdminContextHolder.clear();
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return false;
    }

    private boolean isPublicPath(String path) {
        return PUBLIC_PATH_LOGIN.equals(path);
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
