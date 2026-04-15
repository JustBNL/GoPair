package com.gopair.gateway.filter;

import io.micrometer.tracing.Tracer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import com.gopair.gateway.config.GatewayAuthProperties;

import jakarta.annotation.PostConstruct;

/**
 * 请求日志全局过滤器
 *
 * 记录请求的 method / path / statusCode / duration / traceId，
 * 并将 traceId 注入响应头供客户端追踪。
 *
 * 日志分级策略：5xx 或超过阈值（默认 3s）使用 WARN，其余 INFO。
 * 阈值可通过 gopair.gateway.slow-request-threshold-ms 动态配置。
 *
 * @author gopair
 */
@Slf4j
@Component
public class RequestLoggingGlobalFilter implements GlobalFilter, Ordered {

    private final Tracer tracer;
    private final GatewayAuthProperties gatewayAuthProperties;

    @Autowired
    public RequestLoggingGlobalFilter(@Nullable Tracer tracer,
                                     GatewayAuthProperties gatewayAuthProperties) {
        this.tracer = tracer;
        this.gatewayAuthProperties = gatewayAuthProperties;
    }

    @PostConstruct
    public void init() {
        log.info("[网关服务] 请求日志过滤器初始化完成，慢请求阈值={}ms",
                gatewayAuthProperties.getSlowRequestThresholdMs());
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        String method = request.getMethod().name();

        long startTime = System.currentTimeMillis();
        String traceId = getTraceId();

        log.info("[网关请求] 开始处理 - 方法: {}, 路径: {}, traceId: {}",
                method, path, traceId != null ? traceId : "N/A");

        return chain.filter(exchange)
                .doFinally(signalType -> {
                    long duration = System.currentTimeMillis() - startTime;
                    long threshold = gatewayAuthProperties.getSlowRequestThresholdMs();

                    ServerHttpResponse response = exchange.getResponse();
                    int statusCode = response.getStatusCode() != null
                            ? response.getStatusCode().value() : 0;

                    if (statusCode >= 500 || duration > threshold) {
                        log.warn("[网关请求] 处理完成 - 方法: {}, 路径: {}, 状态码: {}, 耗时: {}ms, traceId: {}",
                                method, path, statusCode, duration, traceId != null ? traceId : "N/A");
                    } else {
                        log.info("[网关请求] 处理完成 - 方法: {}, 路径: {}, 状态码: {}, 耗时: {}ms, traceId: {}",
                                method, path, statusCode, duration, traceId != null ? traceId : "N/A");
                    }
                });
    }

    private String getTraceId() {
        if (tracer != null) {
            var span = tracer.currentSpan();
            if (span != null) {
                return span.context().traceId();
            }
        }
        return null;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
} 