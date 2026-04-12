package com.gopair.common.config;

import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.validation.annotation.Validated;

/**
 * JWT配置属性类，统一管理所有服务的JWT配置。
 *
 * * [核心策略]
 * - 各服务通过 {@code @EnableConfigurationProperties(JwtProperties.class)} 显式注册为 Bean，
 *   保证 common 作为共享库不强制注入，适用于多服务共享配置契约的场景。
 *
 * @author gopair
 */
@Validated
@Data
@RefreshScope
@ConfigurationProperties(prefix = "gopair.jwt")
public class JwtProperties {

    /**
     * JWT签名密钥
     */
    @NotBlank
    private String secret;

    /**
     * JWT令牌过期时间（毫秒）
     */
    @Positive
    private long expiration;

    /**
     * JWT签发者
     */
    private String issuer = "gopair";

    /**
     * 是否启用JWT验证
     */
    private boolean enabled = true;
}
