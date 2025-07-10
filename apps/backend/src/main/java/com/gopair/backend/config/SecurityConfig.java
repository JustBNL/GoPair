package com.gopair.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // 禁用 CSRF 防护
                .csrf(csrf -> csrf.disable())

                .authorizeHttpRequests(auth -> auth
                        // .requestMatchers("/api/match/**").permitAll() // 您已经关闭了这一行，很好！
                        // 这条规则意味着：所有其他请求都需要身份认证
                        .anyRequest().authenticated()
                )
                // 明确指定启用 Basic Auth 认证方式
                .httpBasic(Customizer.withDefaults());

        return http.build();
    }
}