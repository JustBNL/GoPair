package com.gopair.userservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 临时安全配置类
 * 
 * 暂时放行测试接口，后续会迁移到网关统一管理
 * 
 * @author gopair
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(authz -> authz
                .anyRequest().permitAll()                     // 临时放行所有接口，方便测试
            )
            .csrf(csrf -> csrf.disable())                    // 暂时禁用CSRF
            .formLogin(form -> form.disable())               // 禁用表单登录
            .httpBasic(basic -> basic.disable());            // 禁用基础认证
            
        return http.build();
    }
} 