package com.gopair.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT配置属性类
 * 
 * @author gopair
 */
@Data
@ConfigurationProperties(prefix = "gopair.jwt")
public class JwtProperties {

    /**
     * JWT密钥
     */
    private String secret;

    /**
     * JWT令牌过期时间（小时）
     */
    private Long expiration = 24L;

    /**
     * JWT签发者
     */
    private String issuer = "gopair";

    /**
     * 是否启用JWT验证
     */
    private boolean enabled = true;
} 