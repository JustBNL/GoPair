package com.gopair.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * 网关安全配置
 * 
 * 配置Spring Security for WebFlux，暂时允许所有请求通过
 * 
 * @author gopair
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    /**
     * 配置安全过滤链
     * 
     * 暂时禁用所有安全检查，确保网关能正常启动和路由
     */
    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
            // 禁用CSRF
            .csrf(csrf -> csrf.disable())
            
            // 禁用表单登录
            .formLogin(form -> form.disable())
            
            // 禁用HTTP Basic认证
            .httpBasic(basic -> basic.disable())
            
            // 允许所有请求（暂时用于调试）
            .authorizeExchange(exchanges -> exchanges
                .anyExchange().permitAll()
            )
            
            .build();
    }
} 