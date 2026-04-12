package com.gopair.gateway.config;

import brave.baggage.BaggageField;
import brave.baggage.CorrelationScopeConfig;
import brave.context.slf4j.MDCScopeDecorator;
import brave.propagation.CurrentTraceContext;
import com.gopair.common.constants.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * Brave 链路追踪与 MDC 桥接配置
 *
 * 将 Baggage 中的 userId / nickname 字段自动同步到当前线程的 MDC，
 * 使下游服务日志中自动携带用户身份信息，无需手动传递 Context。
 *
 * 技术选型说明：
 * - RequestLoggingGlobalFilter 使用 io.micrometer.tracing.Tracer（门面，符合 Spring 规范）
 * - JwtAuthenticationGatewayFilter 使用 brave.Tracer（原生，用于 BaggageField.updateValue）
 * - 两者底层共享同一 Brave Span，traceId 完全一致，无版本冲突
 *
 * @author gopair
 */
@Slf4j
@Configuration
public class TracingMdcConfiguration {

    @PostConstruct
    public void init() {
        log.info("[网关服务] 链路追踪MDC配置初始化完成");
    }

    /**
     * 构建 MDC 作用域装饰器。
     *
     * flushOnUpdate 确保每次 Baggage 值更新时立即同步到 MDC，
     * 适用于响应式环境中 Span 上下文切换频繁的场景。
     */
    @Bean
    public CurrentTraceContext.ScopeDecorator mdcScopeDecorator() {
        return MDCScopeDecorator.newBuilder()
                .add(CorrelationScopeConfig.SingleCorrelationField.newBuilder(
                        BaggageField.create(SystemConstants.MDC_USER_ID))
                        .flushOnUpdate().build())
                .add(CorrelationScopeConfig.SingleCorrelationField.newBuilder(
                        BaggageField.create(SystemConstants.MDC_NICKNAME))
                        .flushOnUpdate().build())
                .build();
    }
}
