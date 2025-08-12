package com.gopair.framework.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Web安全配置类
 * 
 * 为MVC服务提供基础的安全配置，如密码编码器等
 * 
 * @author GoPair Team
 * @since 2024-01-01
 */
@Configuration
public class WebSecurityConfig {

    /**
     * 密码编码器
     * 使用BCrypt算法进行密码加密
     * 
     * @return BCryptPasswordEncoder实例
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
} 