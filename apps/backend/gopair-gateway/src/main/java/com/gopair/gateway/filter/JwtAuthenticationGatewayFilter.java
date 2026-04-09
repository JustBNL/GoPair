package com.gopair.gateway.filter;

import brave.Tracer;
import brave.baggage.BaggageField;
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
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

import jakarta.annotation.PostConstruct;

/**
 * JWT认证网关过滤器
 *
 * 从Cookie或Authorization头中获取JWT令牌并进行验证，将用户信息传递给下游服务。
 *
 * 追踪增强：
 * JWT验证成功后，将 userId/nickname 注入 Brave BaggageField，
 * 配合 TracingMdcConfiguration 的 MDCScopeDecorator，
 * 使下游服务日志中自动携带 userId 和 nickname。
 *
 * 注意：使用 brave.Tracer（Brave 原生）确保与 BaggageField.updateValue() 的
 * brave.propagation.TraceContext 类型兼容。
 *
 * @author gopair
 */
@Slf4j
@Component
public class JwtAuthenticationGatewayFilter implements GlobalFilter, Ordered {

    private static final BaggageField USER_ID_BAGGAGE = BaggageField.create(MessageConstants.MDC_USER_ID);
    private static final BaggageField NICKNAME_BAGGAGE = BaggageField.create(MessageConstants.MDC_NICKNAME);

    private final JwtProperties jwtProperties;
    private final GatewayAuthProperties gatewayAuthProperties;

    /**
     * Brave Tracer 可选注入，仅在 micrometer-tracing-bridge-brave 存在时有效
     * 使用 brave.Tracer 而非 io.micrometer.tracing.Tracer，
     * 确保 span.context() 返回 brave.propagation.TraceContext
     */
    @Nullable
    private final Tracer tracer;

    private static final String JWT_COOKIE_NAME = MessageConstants.JWT_COOKIE_NAME;
    private static final String AUTHORIZATION_HEADER = MessageConstants.AUTHORIZATION_HEADER;
    private static final String BEARER_PREFIX = MessageConstants.BEARER_PREFIX;
    private static final String USER_ID_HEADER = MessageConstants.HEADER_USER_ID;
    private static final String NICKNAME_HEADER = MessageConstants.HEADER_NICKNAME;

    /**
     * 启动时预解析白名单路径，避免每次请求都 split
     */
    private List<String> preParsedSkipAuthPaths;

    public JwtAuthenticationGatewayFilter(JwtProperties jwtProperties,
                                          GatewayAuthProperties gatewayAuthProperties,
                                          @Nullable Tracer tracer) {
        this.jwtProperties = jwtProperties;
        this.gatewayAuthProperties = gatewayAuthProperties;
        this.tracer = tracer;
    }

    @PostConstruct
    public void init() {
        preParsedSkipAuthPaths = parseSkipAuthPaths(gatewayAuthProperties.getSkipAuthPaths());
        log.info("[网关服务] JWT认证过滤器初始化完成，Baggage追踪增强={}, 白名单路径={}",
                tracer != null ? "已启用" : "未启用(无Tracer)",
                preParsedSkipAuthPaths);
    }

    /**
     * 预解析白名单路径字符串，支持精确匹配和带尾部斜杠的精确匹配。
     */
    private List<String> parseSkipAuthPaths(String skipAuthPathsStr) {
        if (!StringUtils.hasText(skipAuthPathsStr)) {
            return List.of();
        }
        return List.of(skipAuthPathsStr.split(",")).stream()
                .map(String::trim)
                .filter(p -> !p.isEmpty())
                .collect(Collectors.toList());
    }

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
            return handleAuthenticationFailure(exchange.getResponse(),
                    GatewayErrorCode.TOKEN_NOT_FOUND.getCode(),
                    GatewayErrorCode.TOKEN_NOT_FOUND.getMessage());
        }

        log.info("[网关认证] 获取令牌 - 来源: {}, 路径: {}", tokenSource, path);

        // 一次解析，同一 secret 只解析一次
        String userId;
        String nickname;
        try {
            userId = JwtUtils.getUserIdFromToken(token, jwtProperties.getSecret());
            nickname = JwtUtils.getNicknameFromToken(token, jwtProperties.getSecret());
        } catch (Exception e) {
            log.warn("[网关认证] 认证失败 - 路径: {}, 原因: JWT签名验证失败({})", path, e.getClass().getSimpleName());
            return handleAuthenticationFailure(exchange.getResponse(),
                    GatewayErrorCode.TOKEN_VALIDATION_FAILED.getCode(),
                    GatewayErrorCode.TOKEN_VALIDATION_FAILED.getMessage());
        }

        if (!StringUtils.hasText(userId) || !StringUtils.hasText(nickname)) {
            log.warn("[网关认证] 认证失败 - 路径: {}, 原因: 无法从令牌中提取用户信息", path);
            return handleAuthenticationFailure(exchange.getResponse(),
                    GatewayErrorCode.INVALID_USER_INFO.getCode(),
                    GatewayErrorCode.INVALID_USER_INFO.getMessage());
        }

        log.info("[网关认证] 认证成功 - 用户: {}, ID: {}, 路径: {}", nickname, userId, path);

        // 将用户信息注入 Brave BaggageField
        injectUserBaggage(userId, nickname);

        // 将用户信息添加到请求头，传递给下游服务
        ServerHttpRequest modifiedRequest = request.mutate()
                .header(USER_ID_HEADER, userId)
                .header(NICKNAME_HEADER, nickname)
                .build();

        return chain.filter(exchange.mutate().request(modifiedRequest).build())
                .contextWrite(ctx -> ctx
                        .put("userId", userId)
                        .put("nickname", nickname)
                );
    }

    /**
     * 将 userId/nickname 注入 Brave BaggageField
     *
     * BaggageField 通过 B3 传播协议随 HTTP 请求头传递到下游服务，
     * 下游服务的 TracingMdcConfiguration（若已配置）会自动将其写入 MDC。
     */
    private void injectUserBaggage(String userId, String nickname) {
        if (tracer == null) {
            return;
        }
        try {
            brave.Span span = tracer.currentSpan();
            if (span != null) {
                USER_ID_BAGGAGE.updateValue(span.context(), userId);
                NICKNAME_BAGGAGE.updateValue(span.context(), nickname);
                log.debug("[网关认证] 已将 userId={}, nickname={} 注入 BaggageField", userId, nickname);
            } else {
                log.debug("[网关认证] 当前无活跃 Span，跳过 BaggageField 注入");
            }
        } catch (Exception e) {
            log.debug("[网关认证] 注入 BaggageField 失败（非致命）: {}", e.getMessage());
        }
    }

    /**
     * 检查是否为白名单路径，使用精确匹配或带尾部斜杠的精确匹配。
     * 例如配置 "/ws" 可匹配 "/ws" 和 "/ws/"。
     */
    private boolean isSkipAuthPath(String path) {
        return preParsedSkipAuthPaths.stream()
                .anyMatch(skipPath -> path.equals(skipPath) || path.equals(skipPath + "/"));
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
     * 处理认证失败（JSON 消息经过转义，防止注入）
     */
    private Mono<Void> handleAuthenticationFailure(ServerHttpResponse response, int code, String message) {
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        String safeMessage = message
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "");
        String responseBody = String.format(
                "{\"code\":%d,\"message\":\"%s\",\"data\":null,\"success\":false}",
                code, safeMessage
        );
        DataBuffer buffer = response.bufferFactory().wrap(responseBody.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        // 在 RequestLoggingGlobalFilter 之后执行
        return Ordered.HIGHEST_PRECEDENCE + 100;
    }
}
