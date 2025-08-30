package com.gopair.gateway.config;

import brave.baggage.BaggageField;
import brave.baggage.CorrelationScopeConfig;
import brave.context.slf4j.MDCScopeDecorator;
import brave.propagation.CurrentTraceContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Tracing MDC配置
 * 
 * 配置Brave将Baggage中的特定字段自动桥接到MDC中
 * 这样在响应式环境下，userId和nickname也能出现在日志中
 * 
 * @author gopair
 */
@Configuration
public class TracingMdcConfiguration {

    /**
     * 配置MDC scope decorator
     * 
     */
    @Bean
    public CurrentTraceContext.ScopeDecorator mdcScopeDecorator() {
        return MDCScopeDecorator.newBuilder()
                .add(CorrelationScopeConfig.SingleCorrelationField.newBuilder(BaggageField.create("userId"))
                        .flushOnUpdate().build())
                .add(CorrelationScopeConfig.SingleCorrelationField.newBuilder(BaggageField.create("nickname"))
                        .flushOnUpdate().build())
                .build();
    }
} 