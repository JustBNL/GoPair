package com.gopair.gateway.filter;

import brave.Tracer;
import brave.baggage.BaggageField;
import com.gopair.common.constants.SystemConstants;
import com.gopair.common.config.JwtProperties;
import com.gopair.gateway.config.GatewayAuthProperties;
import com.gopair.gateway.enums.GatewayErrorCode;
import com.gopair.gateway.util.JwtUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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

    private static final BaggageField USER_ID_BAGGAGE = BaggageField.create(SystemConstants.MDC_USER_ID);
    private static final BaggageField NICKNAME_BAGGAGE = BaggageField.create(SystemConstants.MDC_NICKNAME);

    private final JwtProperties jwtProperties;
    private final GatewayAuthProperties gatewayAuthProperties;
    private final ObjectMapper objectMapper;

    @Nullable
    private final Tracer tracer;

    private static final String JWT_COOKIE_NAME = SystemConstants.JWT_COOKIE_NAME;
    private static final String AUTHORIZATION_HEADER = SystemConstants.AUTHORIZATION_HEADER;
    private static final String BEARER_PREFIX = SystemConstants.BEARER_PREFIX;
    private static final String USER_ID_HEADER = SystemConstants.HEADER_USER_ID;
    private static final String NICKNAME_HEADER = SystemConstants.HEADER_NICKNAME;

    /**
     * 启动时白名单路径快照，仅用于日志展示。
     * 实际认证时每次请求实时读取最新配置。
     */
    private List<String> skipAuthPathsSnapshot;

    /**
     * Baggage 注入失败告警标记，应用生命周期内只打印一次 WARN。
     * volatile 保证写入对所有读取线程立即可见。
     */
    private volatile boolean baggageInjectionFailedWarned = false;

    public JwtAuthenticationGatewayFilter(JwtProperties jwtProperties,
                                         GatewayAuthProperties gatewayAuthProperties,
                                         @Nullable Tracer tracer,
                                         ObjectMapper objectMapper) {
        this.jwtProperties = jwtProperties;
        this.gatewayAuthProperties = gatewayAuthProperties;
        this.tracer = tracer;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        skipAuthPathsSnapshot = parseSkipAuthPaths(gatewayAuthProperties.getSkipAuthPaths());
        log.info("[网关服务] JWT认证过滤器初始化完成，Baggage追踪增强={}, 白名单路径（启动时快照）={}",
                tracer != null ? "已启用" : "未启用(无Tracer)",
                skipAuthPathsSnapshot);
        log.info("[网关服务] 注意：白名单配置支持运行时动态刷新，无需重启服务");
    }

    /**
     * 预解析白名单路径字符串，支持精确匹配和带尾部斜杠的精确匹配。
     *
     * 注意：由于 GatewayAuthProperties 已标注 @RefreshScope，
     * 此方法在每次请求时实时读取最新配置，确保配置变更即时生效。
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

        // 实时读取最新白名单配置，确保 Nacos 配置变更即时生效
        Set<String> currentSkipAuthPathsSet = new HashSet<>(parseSkipAuthPaths(gatewayAuthProperties.getSkipAuthPaths()));

        // 检查是否为白名单路径
        if (isSkipAuthPath(path, currentSkipAuthPathsSet)) {
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

        // 单次解析，同时获取 userId 和 nickname，避免重复验签
        String userId;
        String nickname;
        try {
            JwtUtils.JwtUserInfo userInfo = JwtUtils.getUserInfoFromToken(token, jwtProperties.getSecret());
            if (userInfo == null) {
                throw new IllegalArgumentException("Token解析结果为空");
            }
            userId = userInfo.userId();
            nickname = userInfo.nickname();
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

        // 将用户信息注入 Brave BaggageField
        injectUserBaggage(userId, nickname);

        // 将用户信息添加到请求头，传递给下游服务
        // URL 编码 nickname（UTF-8）：绕过 HTTP Header 非 ASCII 字符限制
        // 下游 ContextInitFilter 会进行 URL 解码还原
        String encodedNickname;
        try {
            encodedNickname = URLEncoder.encode(nickname, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("[网关认证] nickname URL编码失败，降级使用原始值: {}", nickname);
            encodedNickname = nickname;
        }
        ServerHttpRequest modifiedRequest = request.mutate()
                .header(USER_ID_HEADER, userId)
                .header(NICKNAME_HEADER, encodedNickname)
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
     *
     * 可观测性保障：Baggage 注入失败时下游日志中 userId/nickname 将缺失，
     * 为防止此问题被长期忽视，首次失败时记录一次 WARN 级别告警。
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
            if (!baggageInjectionFailedWarned) {
                baggageInjectionFailedWarned = true;
                log.warn("[网关认证] BaggageField 注入失败，下游日志中将缺失 userId/nickname: {}",
                        e.getMessage());
            } else {
                log.debug("[网关认证] BaggageField 注入失败: {}", e.getMessage());
            }
        }
    }

    /**
     * 检查是否为白名单路径，使用 HashSet 查找（O(1)），支持尾部斜杠变体。
     * 例如配置 "/ws" 可匹配 "/ws" 和 "/ws/"。
     *
     * @param path 当前请求路径
     * @param skipAuthPathsSet 当前的白名单路径集合（每次请求实时获取）
     */
    private boolean isSkipAuthPath(String path, Set<String> skipAuthPathsSet) {
        return skipAuthPathsSet.contains(path) || skipAuthPathsSet.contains(path + "/");
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
     * 处理认证失败响应，使用 ObjectMapper 序列化 JSON，彻底消除手拼 JSON 的注入风险。
     */
    private Mono<Void> handleAuthenticationFailure(ServerHttpResponse response, int code, String message) {
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        try {
            AuthFailureResponse authResponse = new AuthFailureResponse(code, message, null, false);
            byte[] bytes = objectMapper.writeValueAsBytes(authResponse);
            DataBuffer buffer = response.bufferFactory().wrap(bytes);
            return response.writeWith(Mono.just(buffer));
        } catch (JsonProcessingException e) {
            log.error("[网关认证] JSON序列化失败: {}", e.getMessage());
            byte[] fallback = "{\"code\":-1,\"message\":\"Internal server error\",\"data\":null,\"success\":false}"
                    .getBytes(StandardCharsets.UTF_8);
            DataBuffer fallbackBuffer = response.bufferFactory().wrap(fallback);
            return response.writeWith(Mono.just(fallbackBuffer));
        }
    }

    /**
     * 认证失败响应 DTO，字段 final 保证序列化结构稳定。
     */
    private static class AuthFailureResponse {
        private final int code;
        private final String message;
        private final Object data;
        private final boolean success;

        AuthFailureResponse(int code, String message, Object data, boolean success) {
            this.code = code;
            this.message = message;
            this.data = data;
            this.success = success;
        }

        public int getCode() { return code; }
        public String getMessage() { return message; }
        public Object getData() { return data; }
        public boolean isSuccess() { return success; }
    }

    @Override
    public int getOrder() {
        // 在 RequestLoggingGlobalFilter 之后执行
        return Ordered.HIGHEST_PRECEDENCE + 100;
    }
}
