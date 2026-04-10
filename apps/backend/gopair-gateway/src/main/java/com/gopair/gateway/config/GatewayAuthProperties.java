package com.gopair.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 网关认证配置属性类
 *
 * @author gopair
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "gopair.gateway")
public class GatewayAuthProperties {

    /**
     * 跳过认证的路径
     */
    private String skipAuthPaths;

    /**
     * 慢请求阈值（毫秒），超过此阈值则日志级别升为 WARN。
     * 默认值 3000ms，参考：用户对单次 HTTP 响应的可接受等待上限约为 3 秒。
     */
    private long slowRequestThresholdMs = 3000L;
} 