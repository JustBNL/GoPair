package com.gopair.websocketservice.handler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.URI;
import java.util.Map;

/**
 * WebSocket 握手拦截器。
 *
 * <p>职责：在 WebSocket 握手阶段从 URL 查询参数中提取用户认证信息，
 * 存入 WebSocket Session 的属性中，供后续处理器使用。</p>
 *
 * <p>支持的查询参数：
 * <ul>
 *   <li>userId — 用户 ID（必需）</li>
 *   <li>nickname — 用户昵称（必需）</li>
 * </ul>
 * 示例：/api/ws/connect?userId=123&nickname=test</p>
 *
 * <p>设计说明：相比 HTTP 请求头，查询参数是更通用的 WebSocket 认证方式，
 * 兼容所有 WebSocket 客户端（浏览器 JS、JSR-356、Tyrus、OkHttp 等），
 * 不受跨域和握手头限制。</p>
 */
@Slf4j
@Component
public class WebSocketHandshakeInterceptor implements HandshakeInterceptor {

    public static final String ATTR_USER_ID = "ws_user_id";
    public static final String ATTR_NICKNAME = "ws_nickname";
    public static final String ATTR_HEADERS = "ws_headers";

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   org.springframework.web.socket.WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {
        try {
            URI uri = request.getURI();
            String query = uri.getQuery();

            log.debug("[握手拦截] WebSocket 握手开始: uri={}", uri);

            // 从查询参数中提取 userId 和 nickname
            String userId = extractQueryParam(query, "userId");
            String nickname = extractQueryParam(query, "nickname");

            if (userId != null && nickname != null) {
                attributes.put(ATTR_USER_ID, userId);
                attributes.put(ATTR_NICKNAME, nickname);
                log.info("[握手拦截] 从查询参数提取认证信息: userId={}, nickname={}", userId, nickname);
            } else {
                // 降级：从 HTTP 请求头中读取（支持生产环境通过网关转发头）
                String userIdHeader = extractHeader(request, "X-User-Id");
                String nicknameHeader = extractHeader(request, "X-Nickname");

                if (userIdHeader != null && nicknameHeader != null) {
                    attributes.put(ATTR_USER_ID, userIdHeader);
                    attributes.put(ATTR_NICKNAME, nicknameHeader);
                    log.info("[握手拦截] 从 HTTP 头提取认证信息: userId={}, nickname={}", userIdHeader, nicknameHeader);
                } else {
                    log.debug("[握手拦截] 未提供认证信息（userId 或 nickname 为空）");
                }
            }

            // 将所有请求头存入属性，供 GlobalWebSocketHandler 使用
            if (request instanceof ServletServerHttpRequest servletRequest) {
                attributes.put(ATTR_HEADERS, servletRequest.getHeaders());
            }

            return true;
        } catch (Exception e) {
            log.error("[握手拦截] WebSocket 握手前置处理失败", e);
            return true; // 允许握手继续，不阻断连接
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                org.springframework.web.socket.WebSocketHandler wsHandler,
                                Exception exception) {
        if (exception != null) {
            log.error("[握手拦截] WebSocket 握手后处理异常", exception);
        }
    }

    private String extractQueryParam(String query, String name) {
        if (query == null || query.isEmpty()) {
            return null;
        }
        for (String param : query.split("&")) {
            int eq = param.indexOf('=');
            if (eq > 0) {
                String key = param.substring(0, eq).trim();
                if (name.equals(key)) {
                    String value = param.substring(eq + 1).trim();
                    return value.isEmpty() ? null : value;
                }
            }
        }
        return null;
    }

    private String extractHeader(ServerHttpRequest request, String name) {
        try {
            var values = request.getHeaders().get(name);
            if (values != null && !values.isEmpty()) {
                return values.get(0);
            }
        } catch (Exception e) {
            log.debug("[握手拦截] 读取请求头失败: {}: {}", name, e.getMessage());
        }
        return null;
    }
}
