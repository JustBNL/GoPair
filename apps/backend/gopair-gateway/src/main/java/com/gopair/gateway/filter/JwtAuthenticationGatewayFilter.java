package com.gopair.gateway.filter;

import com.gopair.common.util.JwtUtils;
import com.gopair.common.exception.ExceptionResolver;
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
import org.springframework.util.CollectionUtils;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * JWT认证全局过滤器
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

    /**
     * 不需要进行JWT验证的路径 (临时使用硬编码，避免配置读取问题)
     */
    private static final List<String> SKIP_AUTH_PATHS = Arrays.asList(
            "/api/v1/users/login",
            "/api/v1/users/register",
            "/api/v1/health",
            "/doc.html",
            "/swagger-ui.html",
            "/v3/api-docs"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // 检查是否是不需要验证的路径
        if (isSkipAuthPath(path)) {
            log.debug("跳过JWT验证的路径: {}", path);
            return chain.filter(exchange);
        }

        // 从Cookie中获取JWT令牌
        String token = getTokenFromCookie(request);

        if (!StringUtils.hasText(token)) {
            log.warn("未找到JWT令牌，路径: {}", path);
            return unauthorizedResponse(exchange, "未提供认证令牌");
        }

        // 验证JWT令牌
        if (!JwtUtils.validateToken(token, jwtSecret)) {
            log.warn("JWT令牌验证失败，路径: {}", path);
            return unauthorizedResponse(exchange, "认证令牌无效或已过期");
        }

        try {
            // 从JWT令牌中提取用户信息
            String username = JwtUtils.getUsernameFromToken(token, jwtSecret);
            String userId = JwtUtils.getUserIdFromToken(token, jwtSecret);

            if (!StringUtils.hasText(username)) {
                log.warn("JWT令牌中未找到用户名，路径: {}", path);
                return unauthorizedResponse(exchange, "认证令牌格式错误");
            }

            log.debug("JWT验证成功，用户: {}, ID: {}, 路径: {}", username, userId, path);

            // 将用户信息添加到请求头中，传递给下游服务
            ServerHttpRequest mutatedRequest = request.mutate()
                    .header(USERNAME_HEADER, username)
                    .header(USER_ID_HEADER, userId != null ? userId : "")
                    .build();

            return chain.filter(exchange.mutate().request(mutatedRequest).build());

        } catch (Exception e) {
            log.error("JWT令牌解析失败，路径: {}, 错误: {}", path, e.getMessage());
            return unauthorizedResponse(exchange, "认证令牌解析失败");
        }
    }

    /**
     * 从Cookie中获取JWT令牌
     * 
     * @param request HTTP请求
     * @return JWT令牌字符串，如果未找到则返回null
     */
    private String getTokenFromCookie(ServerHttpRequest request) {
        MultiValueMap<String, HttpCookie> cookies = request.getCookies();
        
        if (CollectionUtils.isEmpty(cookies)) {
            return null;
        }

        List<HttpCookie> tokenCookies = cookies.get(JWT_COOKIE_NAME);
        if (CollectionUtils.isEmpty(tokenCookies)) {
            return null;
        }

        return tokenCookies.get(0).getValue();
    }

    /**
     * 检查是否是需要跳过认证的路径
     * 
     * @param path 请求路径
     * @return 如果需要跳过认证则返回true
     */
    private boolean isSkipAuthPath(String path) {
        return SKIP_AUTH_PATHS.stream().anyMatch(skipPath -> 
            path.startsWith(skipPath) || path.contains(skipPath)
        );
    }

    /**
     * 返回未授权响应
     * 
     * @param exchange 网关交换器
     * @param message 错误消息
     * @return 响应式结果
     */
    private Mono<Void> unauthorizedResponse(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);

        String body = String.format("{\"code\":401,\"message\":\"%s\",\"data\":null}", message);
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        
        return response.writeWith(Flux.just(buffer));
    }

    @Override
    public int getOrder() {
        return -100; // 高优先级，确保在路由之前执行
    }
} 