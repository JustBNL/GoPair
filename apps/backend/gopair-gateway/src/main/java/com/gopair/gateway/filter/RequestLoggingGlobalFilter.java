package com.gopair.gateway.filter;

import io.micrometer.tracing.Tracer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 请求日志全局过滤器
 * 
 * 负责记录请求的开始时间、结束时间和总耗时
 * 并将traceId添加到响应头中
 * 
 * @author gopair
 */
@Slf4j
@Component
public class RequestLoggingGlobalFilter implements GlobalFilter, Ordered {

    private final Tracer tracer;
    
    public RequestLoggingGlobalFilter(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        String method = request.getMethod().name();
        
        // 记录请求开始时间
        long startTime = System.currentTimeMillis();
        
        // 获取当前traceId
        String traceId = getTraceId();
        
        // 记录请求开始日志
        log.info("[网关请求] 开始处理 - 方法: {}, 路径: {}, traceId: {}", method, path, traceId);
        
        return chain.filter(exchange)
                .doFinally(signalType -> {
                    // 计算请求耗时
                    long duration = System.currentTimeMillis() - startTime;
                    
                    ServerHttpResponse response = exchange.getResponse();
                    int statusCode = response.getStatusCode() != null ? 
                        response.getStatusCode().value() : 0;
                    
                    // 移除手动TraceId响应头设置，依赖Brave自动处理
                    
                    // 记录请求完成日志
                    log.info("[网关请求] 处理完成 - 方法: {}, 路径: {}, 状态码: {}, 耗时: {}ms, traceId: {}", 
                            method, path, statusCode, duration, traceId);
                });
    }

    /**
     * 获取当前traceId
     */
    private String getTraceId() {
        if (tracer != null && tracer.currentSpan() != null) {
            return tracer.currentSpan().context().traceId();
        }
        return null;
    }

    @Override
    public int getOrder() {
        // 设置为最高优先级，确保在所有其他过滤器之前执行
        return Ordered.HIGHEST_PRECEDENCE;
    }
} 