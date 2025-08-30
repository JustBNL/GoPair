package com.gopair.gateway.filter;

import com.gopair.common.enums.impl.CommonErrorCode;
import com.gopair.common.util.JwtUtils;
import com.gopair.common.constants.MessageConstants;
import com.gopair.gateway.config.GatewayAuthProperties;
import com.gopair.gateway.config.JwtProperties;
import com.gopair.gateway.enums.GatewayErrorCode;

import lombok.extern.slf4j.Slf4j;
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
 * JWT认证网关过滤器
 *
 * 从Cookie中获取JWT令牌并进行验证，将用户信息传递给下游服务
 *
 * @author gopair
 */
@Slf4j
@Component
public class JwtAuthenticationGatewayFilter implements GlobalFilter, Ordered {

    private final JwtProperties jwtProperties;
    private final GatewayAuthProperties gatewayAuthProperties;

    public JwtAuthenticationGatewayFilter(JwtProperties jwtProperties, GatewayAuthProperties gatewayAuthProperties) {
        this.jwtProperties = jwtProperties;
        this.gatewayAuthProperties = gatewayAuthProperties;
    }

    /**
     * JWT令牌在Cookie中的名称
     */
    private static final String JWT_COOKIE_NAME = MessageConstants.JWT_COOKIE_NAME;
    
    /**
     * Authorization请求头名称
     */
    private static final String AUTHORIZATION_HEADER = MessageConstants.AUTHORIZATION_HEADER;
    
    /**
     * Bearer令牌前缀
     */
    private static final String BEARER_PREFIX = MessageConstants.BEARER_PREFIX;

    /**
     * 传递给下游服务的用户ID头名称
     */
    private static final String USER_ID_HEADER = MessageConstants.HEADER_USER_ID;

    /**
     * 传递给下游服务的昵称头名称
     */
    private static final String NICKNAME_HEADER = MessageConstants.HEADER_NICKNAME;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        log.info("[网关认证] 开始处理 - 路径: {}", path);

        // 检查是否为白名单路径
        if (isSkipAuthPath(path)) {
            log.info("[网关认证] 跳过认证 - 路径: {}", path);
            return chain.filter(exchange);
        }

        // 优先从Authorization请求头获取JWT令牌
        String token = extractTokenFromHeader(request);
        String tokenSource = "Authorization头";
        
        // 如果请求头中没有，再从Cookie中获取
        if (!StringUtils.hasText(token)) {
            token = extractTokenFromCookie(request);
            tokenSource = "Cookie";
        }
        
        // 两处都没有找到token
        if (!StringUtils.hasText(token)) {
            log.warn("[网关认证] 认证失败 - 路径: {}, 原因: 未找到JWT令牌", path);
            return handleAuthenticationFailure(exchange.getResponse(), GatewayErrorCode.TOKEN_NOT_FOUND.getMessage());
        }

        log.info("[网关认证] 获取令牌 - 来源: {}, 路径: {}", tokenSource, path);

        try {
            // 验证JWT令牌
            if (!JwtUtils.validateToken(token, jwtProperties.getSecret())) {
                log.warn("[网关认证] 认证失败 - 路径: {}, 原因: JWT令牌验证失败", path);
                return handleAuthenticationFailure(exchange.getResponse(), GatewayErrorCode.TOKEN_VALIDATION_FAILED.getMessage());
            }

            // 提取用户信息
            String userId = JwtUtils.getUserIdFromToken(token, jwtProperties.getSecret());
            String nickname = JwtUtils.getNicknameFromToken(token, jwtProperties.getSecret());

            if (!StringUtils.hasText(userId) || !StringUtils.hasText(nickname)) {
                log.warn("[网关认证] 认证失败 - 路径: {}, 原因: 无法从令牌中提取用户信息", path);
                return handleAuthenticationFailure(exchange.getResponse(), GatewayErrorCode.INVALID_USER_INFO.getMessage());
            }

            log.info("[网关认证] 认证成功 - 用户: {}, ID: {}, 路径: {}", nickname, userId, path);

            // 将用户信息添加到请求头，传递给下游服务
            ServerHttpRequest modifiedRequest = request.mutate()
                    .header(USER_ID_HEADER, userId)
                    .header(NICKNAME_HEADER, nickname)
                    .build();

            // 将用户信息添加到Reactor Context中，Brave会自动将其桥接到MDC
            return chain.filter(exchange.mutate().request(modifiedRequest).build())
                    .contextWrite(ctx -> ctx
                            .put("userId", userId)
                            .put("nickname", nickname)
                    );

        } catch (Exception e) {
            log.error("[网关认证] 认证异常 - 路径: {}, 异常: {}", path, e.getMessage(), e);
            return handleAuthenticationFailure(exchange.getResponse(), GatewayErrorCode.AUTH_PROCESSING_ERROR.getMessage());
        }
    }

    /**
     * 检查是否为白名单路径
     */
    private boolean isSkipAuthPath(String path) {
        String skipAuthPathsStr = gatewayAuthProperties.getSkipAuthPaths();
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
     * 从Authorization请求头中提取JWT令牌
     */
    private String extractTokenFromHeader(ServerHttpRequest request) {
        List<String> authHeaders = request.getHeaders().get(AUTHORIZATION_HEADER);
        if (authHeaders != null && !authHeaders.isEmpty()) {
            String authHeader = authHeaders.get(0);
            if (StringUtils.hasText(authHeader) && authHeader.startsWith(BEARER_PREFIX)) {
                return authHeader.substring(BEARER_PREFIX.length());
            }
        }
        return null;
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

    /**
     * 处理认证失败（支持错误码对象）
     */
    private Mono<Void> handleAuthenticationFailure(ServerHttpResponse response, GatewayErrorCode errorCode) {
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);

        String responseBody = String.format(
            "{\"code\":%d,\"message\":\"%s\",\"data\":null,\"success\":false}",
            errorCode.getCode(),
            errorCode.getMessage()
        );

        DataBuffer buffer = response.bufferFactory().wrap(responseBody.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        // 设置为在RequestLoggingGlobalFilter之后执行，确保JWT解析在日志记录之后
        return Ordered.HIGHEST_PRECEDENCE + 100;
    }
}