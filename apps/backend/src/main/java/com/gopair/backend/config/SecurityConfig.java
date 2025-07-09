package com.gopair.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // 关闭 CSRF 保护，便于接口测试
                .authorizeHttpRequests(auth -> auth
                        // 对 /api/test/ 和 /api/match/ 下的所有请求，都允许匿名访问
                        .requestMatchers("/api/test/**", "/api/match/**").permitAll()
                        // 除了上面放行的请求外，任何其他请求都需要身份验证
                        .anyRequest().authenticated()
                );
        return http.build();
    }
}