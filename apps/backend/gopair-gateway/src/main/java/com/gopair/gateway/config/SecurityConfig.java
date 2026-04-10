package com.gopair.gateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

import jakarta.annotation.PostConstruct;

/**
 * Spring Security WebFlux 安全基线配置
 *
 * 本配置仅负责安全基础设施的初始化和安全策略基线，不承载认证逻辑。
 * 实际请求认证由 JwtAuthenticationGatewayFilter 处理（两者协同工作）。
 *
 * 当前基线策略：
 * - 禁用 CSRF（网关透传模式天然防护）
 * - 禁用 BasicAuth / FormLogin（由 JWT 过滤器替代）
 * - 所有请求默认 permitAll（具体路径的认证由过滤器链控制）
 *
 * @author gopair
 */
@Slf4j
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @PostConstruct
    public void init() {
        log.info("[网关服务] Spring Security WebFlux 安全基线配置初始化完成");
    }

    /**
     * 构建安全过滤器链基线。
     *
     * 注意：认证逻辑由 JwtAuthenticationGatewayFilter 负责，此处仅设置安全策略基线。
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
            );

        return http.build();
    }

} 