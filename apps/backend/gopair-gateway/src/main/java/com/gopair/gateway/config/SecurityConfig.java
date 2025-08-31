package com.gopair.gateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import reactor.core.publisher.Mono;

import jakarta.annotation.PostConstruct;

/**
 * Spring Security WebFlux 配置类
 * 
 * 配置网关的安全策略，与 JWT 认证过滤器协同工作
 * 
 * @author gopair
 */
@Slf4j
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @PostConstruct
    public void init() {
        log.info("[网关服务] Spring Security WebFlux配置初始化完成");
    }

    /**
     * 配置安全过滤器链
     * 
     * 注意：这里配置为允许所有请求通过，实际的认证逻辑由 JWT 过滤器处理
     * Spring Security 主要用于提供安全基础设施和过滤器链
     */
    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        http
            // 禁用 CSRF 保护（API 网关通常不需要）
            .csrf(csrf -> csrf.disable())
            
            // 禁用 HTTP Basic 认证
            .httpBasic(httpBasic -> httpBasic.disable())
            
            // 禁用表单登录
            .formLogin(formLogin -> formLogin.disable())
            
            // 配置授权规则
            .authorizeExchange(authorizeExchange -> authorizeExchange
                // 允许所有请求通过，认证逻辑由 JWT 过滤器处理
                .anyExchange().permitAll()
            )
            
            // 配置异常处理
            .exceptionHandling(exceptionHandling -> exceptionHandling
                // 可以在这里配置自定义的认证/授权失败处理器
                .authenticationEntryPoint((exchange, e) -> {
                    // 认证失败时的处理逻辑
                    return Mono.empty();
                })
            );

        return http.build();
    }

} 