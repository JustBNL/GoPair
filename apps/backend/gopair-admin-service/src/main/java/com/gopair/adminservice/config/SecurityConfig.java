package com.gopair.adminservice.config;

import com.gopair.adminservice.filter.AdminAuthFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

/**
 * Spring Security 安全配置
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public AdminAuthFilter adminAuthFilter(
            @org.springframework.beans.factory.annotation.Value("${gopair.admin.jwt.secret}") String jwtSecret) {
        return new AdminAuthFilter(jwtSecret);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           AdminAuthFilter adminAuthFilter) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .addFilterBefore(adminAuthFilter, AnonymousAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/admin/auth/login").permitAll()
                .requestMatchers("/doc.html", "/swagger-resources/**", "/v3/api-docs/**", "/webjars/**").permitAll()
                .requestMatchers("/admin/**").authenticated()
                .anyRequest().permitAll()
            )
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable);
        return http.build();
    }
}
