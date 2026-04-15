package com.gopair.gateway.config;

import com.gopair.common.logback.OtelSdkHolder;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.exporter.otlp.logs.OtlpGrpcLogRecordExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.semconv.ResourceAttributes;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Hooks;

import jakarta.annotation.PostConstruct;

/**
 * OpenTelemetry 可观测性配置 - Gateway 专属
 *
 * * [核心策略]
 * - 手动构建 OTel SDK 并显式配置 OTLP gRPC 导出器（地址从 Nacos 配置注入）
 * - 服务实例 ID 唯一标识每次启动，便于日志溯源
 * - 不使用 GlobalOpenTelemetry，通过 OtelSdkHolder 静态持有者在 SDK Bean 和 Appender 之间传递实例，
 *   避免 GlobalOpenTelemetry.get() 先于 set() 调用导致的 auto-configure noop 问题
 *
 * * [执行链路]
 * 1. 读取 OTLP 端点配置（tracing/logs），支持环境变量覆盖
 * 2. 构建 ServiceName + ServiceVersion + InstanceId 的 Resource
 * 3. 创建 OTLP SpanExporter 和 LogExporter，绑定到共享的 SdkTracerProvider / SdkLoggerProvider
 * 4. 构建 OpenTelemetrySdk 并注册到 OtelSdkHolder（供 MdcAwareOtelAppender 使用）
 * 5. 启用响应式 Context 自动传播
 *
 * 注意：不再使用 BeanPostProcessor 安装 OpenTelemetryAppender。
 *
 * @author gopair
 */
@Slf4j
@Configuration
public class GatewayOtelConfiguration {

    @Value("${management.otlp.tracing.endpoint:http://localhost:5081}")
    private String tracingEndpoint;

    @Value("${management.otlp.logging.endpoint:http://localhost:5081}")
    private String loggingEndpoint;

    @Value("${spring.application.name:gateway-service}")
    private String serviceName;

    @Value("${info.app.version:1.0.0}")
    private String serviceVersion;

    @Value("${gopair.otel.authorization:}")
    private String otelAuthorization;

    @Value("${gopair.otel.organization:default}")
    private String otelOrganization;

    @Value("${gopair.otel.stream-name:gateway-logs}")
    private String otelStreamName;

    public GatewayOtelConfiguration() {
    }

    @PostConstruct
    public void init() {
        Hooks.enableAutomaticContextPropagation();
        log.info("[网关服务] OpenTelemetry 可观测性配置初始化完成");
    }

    @Bean
    public OpenTelemetry openTelemetry() {
        String instanceId = System.getProperty("hostname", "unknown") + "_" + ProcessHandle.current().pid();

        Resource resource = Resource.getDefault()
                .merge(Resource.create(Attributes.of(
                        ResourceAttributes.SERVICE_NAME, serviceName,
                        ResourceAttributes.SERVICE_VERSION, serviceVersion,
                        ResourceAttributes.SERVICE_INSTANCE_ID, instanceId
                )));

        // OTLP Span Exporter -> TracerProvider
        OtlpGrpcSpanExporter spanExporter = OtlpGrpcSpanExporter.builder()
                .setEndpoint(tracingEndpoint)
                .build();

        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .setResource(resource)
                .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
                .build();

        // OTLP Log Exporter -> LoggerProvider
        OtlpGrpcLogRecordExporter logExporter = OtlpGrpcLogRecordExporter.builder()
                .setEndpoint(loggingEndpoint)
                .addHeader("authorization", otelAuthorization)
                .addHeader("organization", otelOrganization)
                .addHeader("stream-name", otelStreamName)
                .setCompression("gzip")
                .build();

        SdkLoggerProvider loggerProvider = SdkLoggerProvider.builder()
                .setResource(resource)
                .addLogRecordProcessor(BatchLogRecordProcessor.builder(logExporter).build())
                .build();

        OpenTelemetrySdk openTelemetrySdk = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .setLoggerProvider(loggerProvider)
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .build();

        // 注册到静态 Holder，供 MdcAwareOtelAppender 获取（不使用 GlobalOpenTelemetry，避免冲突）
        OtelSdkHolder.set(openTelemetrySdk);

        log.info("[网关服务] OpenTelemetry SDK 初始化完成 - tracing={}, logs={}, service={}, instance={}",
                tracingEndpoint, loggingEndpoint, serviceName, instanceId);

        return openTelemetrySdk;
    }
}
