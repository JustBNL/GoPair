package com.gopair.framework.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;

/**
 * OpenTelemetry 日志导出配置属性
 *
 * 所有 OTel 日志相关配置集中于此，避免硬编码。
 * 值由 gopair-otel-base.yml 注入，各服务也可通过 application.yml 覆盖。
 *
 * @author gopair
 */
@Data
@RefreshScope
@ConfigurationProperties(prefix = "gopair.otel")
public class OtelLoggingProperties {

    /**
     * OpenObserve Basic Auth Base64 凭证
     */
    private String authorization = "Basic ODI3MjAwMTdAcXEuY29tOjgxNzkwMDE3";

    /**
     * OpenObserve 组织名称
     */
    private String organization = "default";

    /**
     * OpenObserve 日志 Stream 名称
     * 默认为 spring.application.name，可在各服务 application.yml 中覆盖
     */
    private String streamName = "gopair-service";
}
