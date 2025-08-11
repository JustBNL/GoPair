package com.gopair.gateway.filter;

import com.gopair.common.util.JwtUtils;
import com.gopair.common.enums.impl.CommonErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * JWT认证网关过滤器 (WebFlux版本)
 * 
 * 从Cookie中获取JWT令牌并进行验证，将用户信息传递给下游服务
 * 
 * @author gopair
 */
@Slf4j
@Component
public class JwtAuthenticationGatewayFilter implements GlobalFilter, Ordered {

    @Value("${gopair.jwt.secret}")
    private String jwtSecret;

    @Value("${gopair.gateway.skip-auth-paths}")
    private String skipAuthPathsStr;

    /**
     * JWT令牌在Cookie中的名称
     */
    private static final String JWT_COOKIE_NAME = "token";
    
    /**
     * 传递给下游服务的用户ID头名称
     */
    private static final String USER_ID_HEADER = "X-User-Id";
    
    /**
     * 传递给下游服务的用户名头名称
     */
    private static final String USERNAME_HEADER = "X-Username";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        
        log.debug("处理请求路径: {}", path);
        
        // 检查是否为白名单路径
        if (isSkipAuthPath(path)) {
            log.debug("跳过认证，路径: {}", path);
            return chain.filter(exchange);
        }
        
        // 从Cookie中获取JWT令牌
        String token = extractTokenFromCookie(request);
        if (!StringUtils.hasText(token)) {
            log.warn("未找到JWT令牌，路径: {}", path);
            return handleAuthenticationFailure(exchange.getResponse(), "未找到认证令牌");
        }
        
        try {
            // 验证JWT令牌
            if (!JwtUtils.validateToken(token, jwtSecret)) {
                log.warn("JWT令牌验证失败，路径: {}", path);
                return handleAuthenticationFailure(exchange.getResponse(), "令牌验证失败");
            }
            
            // 提取用户信息
            String userId = JwtUtils.getUserIdFromToken(token, jwtSecret);
            String username = JwtUtils.getUsernameFromToken(token, jwtSecret);
            
            if (!StringUtils.hasText(userId) || !StringUtils.hasText(username)) {
                log.warn("无法从令牌中提取用户信息，路径: {}", path);
                return handleAuthenticationFailure(exchange.getResponse(), "无效的用户信息");
            }
            
            log.debug("认证成功，用户: {}, ID: {}, 路径: {}", username, userId, path);
            
            // 将用户信息添加到请求头，传递给下游服务
            ServerHttpRequest modifiedRequest = request.mutate()
                    .header(USER_ID_HEADER, userId)
                    .header(USERNAME_HEADER, username)
                    .build();
            
            return chain.filter(exchange.mutate().request(modifiedRequest).build());
            
        } catch (Exception e) {
            log.error("JWT令牌处理异常，路径: {}", path, e);
            return handleAuthenticationFailure(exchange.getResponse(), "认证处理异常");
        }
    }
    
    /**
     * 检查是否为白名单路径
     */
    private boolean isSkipAuthPath(String path) {
        if (!StringUtils.hasText(skipAuthPathsStr)) {
            return false;
        }
        
        List<String> skipAuthPaths = Arrays.asList(skipAuthPathsStr.split(","));
        return skipAuthPaths.stream()
                .map(String::trim)
                .anyMatch(skipPath -> path.startsWith(skipPath));
    }
    
    /**
     * 从Cookie中提取JWT令牌
     */
    private String extractTokenFromCookie(ServerHttpRequest request) {
        HttpCookie cookie = request.getCookies().getFirst(JWT_COOKIE_NAME);
        return cookie != null ? cookie.getValue() : null;
    }
    
    /**
     * 处理认证失败
     */
    private Mono<Void> handleAuthenticationFailure(ServerHttpResponse response, String message) {
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        
        String responseBody = String.format(
            "{\"code\":%d,\"message\":\"%s\",\"data\":null,\"success\":false}",
            CommonErrorCode.UNAUTHORIZED.getCode(),
            message
        );
        
        DataBuffer buffer = response.bufferFactory().wrap(responseBody.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return -100; // 确保认证过滤器优先执行
    }
} 