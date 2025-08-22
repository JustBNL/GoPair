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
} 