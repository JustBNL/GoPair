package com.gopair.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 基础安全配置类
 * 
 * 提供通用的安全配置，如密码编码器等
 * 
 * @author GoPair Team
 * @since 2024-01-01
 */
@Configuration
public class BaseSecurityConfig {

    /**
     * 密码编码器
     * 
     * @return BCryptPasswordEncoder实例
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
} 