package com.gopair.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * JWT 配置属性类
 *
 * @author gopair
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "gopair.jwt")
public class JwtProperties {

    /**
     * JWT密钥
     */
    private String secret;

    /**
     * JWT过期时间（毫秒）
     */
    private long expiration;
} 