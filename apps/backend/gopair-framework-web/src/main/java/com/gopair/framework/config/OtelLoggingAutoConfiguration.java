package com.gopair.framework.config;

import com.gopair.common.logback.OtelSdkHolder;
import com.gopair.framework.config.properties.OtelLoggingProperties;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.exporter.otlp.logs.OtlpGrpcLogRecordExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.semconv.ResourceAttributes;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenTelemetry 日志导出自动配置（供所有 MVC 服务使用）
 *
 * * [核心策略]
 * - 默认启用（gopair.otel.enabled=true），可按服务禁用
 * - 仅在 Servlet 应用中激活（Gateway 使用独立的 GatewayOtelConfiguration）
 * - 凭证（authorization/organization/stream-name）从 gopair-otel-base.yml 注入，支持环境变量覆盖
 * - 服务实例 ID 唯一标识每次启动，便于日志溯源
 * - 不使用 GlobalOpenTelemetry，通过 OtelSdkHolder 静态持有者在 SDK Bean 和 Appender 之间传递实例
 *
 * * [执行链路]
 * 1. 读取 gopair.otel.* 配置（credentials / organization / stream-name）
 * 2. 构建 SdkLoggerProvider，绑定 OTLP gRPC LogExporter（携带 OpenObserve 认证头）
 * 3. 构建 OpenTelemetrySdk，注册到 OtelSdkHolder（供 MdcAwareOtelAppender 使用）
 * 4. MdcAwareOtelAppender（通过 logback-spring.xml 实例化）在 append() 时从 OtelSdkHolder 获取 SDK
 *
 * 注意：不再使用 BeanPostProcessor 安装 OpenTelemetryAppender。
 *
 * @author gopair
 */
@Slf4j
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(OpenTelemetry.class)
@ConditionalOnProperty(prefix = "gopair.otel", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(OtelLoggingProperties.class)
public class OtelLoggingAutoConfiguration {

    @Value("${spring.application.name:unknown-service}")
    private String serviceName;

    @Value("${info.app.version:1.0.0}")
    private String serviceVersion;

    @Value("${server.port:8080}")
    private int serverPort;

    /**
     * OTLP Log Exporter + LoggerProvider
     */
    @Bean
    @ConditionalOnMissingBean(SdkLoggerProvider.class)
    public SdkLoggerProvider sdkLoggerProvider(OtelLoggingProperties otelProps,
                                               @Value("${management.otlp.logging.endpoint:http://localhost:5081}") String loggingEndpoint) {
        String instanceId = System.getProperty("hostname", "unknown") + "_" + ProcessHandle.current().pid() + "_" + serverPort;

        Resource resource = Resource.getDefault()
                .merge(Resource.create(Attributes.of(
                        ResourceAttributes.SERVICE_NAME, serviceName,
                        ResourceAttributes.SERVICE_VERSION, serviceVersion,
                        ResourceAttributes.SERVICE_INSTANCE_ID, instanceId
                )));

        OtlpGrpcLogRecordExporter logExporter = OtlpGrpcLogRecordExporter.builder()
                .setEndpoint(loggingEndpoint)
                .addHeader("authorization", otelProps.getAuthorization())
                .addHeader("organization", otelProps.getOrganization())
                .addHeader("stream-name", otelProps.getStreamName())
                .setCompression("gzip")
                .build();

        SdkLoggerProvider loggerProvider = SdkLoggerProvider.builder()
                .setResource(resource)
                .addLogRecordProcessor(BatchLogRecordProcessor.builder(logExporter).build())
                .build();

        log.info("[OTel日志] SdkLoggerProvider 初始化完成 - service={}, stream={}, endpoint={}",
                serviceName, otelProps.getStreamName(), loggingEndpoint);

        return loggerProvider;
    }

    /**
     * OpenTelemetry SDK 实例（合并 TracerProvider + LoggerProvider）
     * 不使用 GlobalOpenTelemetry，直接注册到 OtelSdkHolder，
     * 避免 GlobalOpenTelemetry.get() 先于 set() 调用导致的 noop 问题。
     */
    @Bean
    @ConditionalOnMissingBean(OpenTelemetry.class)
    public OpenTelemetry openTelemetrySdk(SdkLoggerProvider sdkLoggerProvider) {
        OpenTelemetrySdk openTelemetrySdk = OpenTelemetrySdk.builder()
                .setLoggerProvider(sdkLoggerProvider)
                .build();

        OtelSdkHolder.set(openTelemetrySdk);

        log.info("[OTel日志] OpenTelemetry SDK 初始化完成 - service={}", serviceName);

        return openTelemetrySdk;
    }
}
