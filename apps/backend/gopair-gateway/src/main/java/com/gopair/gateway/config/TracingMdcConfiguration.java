package com.gopair.gateway.config;

import brave.baggage.BaggageField;
import brave.baggage.CorrelationScopeConfig;
import brave.context.slf4j.MDCScopeDecorator;
import brave.propagation.CurrentTraceContext;
import com.gopair.common.constants.MessageConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * Tracing MDC配置
 * 
 * 配置Brave将Baggage中的特定字段自动桥接到MDC中
 * 这样在响应式环境下，userId和nickname也能出现在日志中
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
     * 配置MDC scope decorator
     * 
     */
    @Bean
    public CurrentTraceContext.ScopeDecorator mdcScopeDecorator() {
        return MDCScopeDecorator.newBuilder()
                .add(CorrelationScopeConfig.SingleCorrelationField.newBuilder(
                        BaggageField.create(MessageConstants.MDC_USER_ID))
                        .flushOnUpdate().build())
                .add(CorrelationScopeConfig.SingleCorrelationField.newBuilder(
                        BaggageField.create(MessageConstants.MDC_NICKNAME))
                        .flushOnUpdate().build())
                .build();
    }
} 