package com.gopair.userservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT配置属性类
 * 
 * @author gopair
 */
@Data
@Component
@ConfigurationProperties(prefix = "gopair.jwt")
public class JwtConfig {
    
    /**
     * JWT密钥
     */
    private String secret;
    
    /**
     * JWT过期时间（毫秒）
     */
    private long expiration;
} 