package com.gopair.framework.config;

import com.gopair.framework.config.properties.ContextProperties;
import com.gopair.framework.context.ContextInitFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.servlet.Filter;

/**
 * 上下文功能配置类
 *
 * 通过过滤器统一管理 traceId 与用户上下文（MDC + ThreadLocal）
 * 从 ContextAutoConfiguration 迁移而来
 */
@Slf4j
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(Filter.class)
@ConditionalOnProperty(prefix = "gopair.context", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(ContextProperties.class)
public class ContextConfiguration {

    @Bean
    public ContextInitFilter contextInitFilter(ContextProperties properties) {
        return new ContextInitFilter(properties);
    }

    @Bean
    public FilterRegistrationBean<ContextInitFilter> contextInitFilterRegistration(ContextInitFilter filter) {
        FilterRegistrationBean<ContextInitFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(filter);
        registration.addUrlPatterns("/*");
        registration.setName("contextInitFilter");
        // 与类上 @Order(Integer.MIN_VALUE + 100) 对齐
        registration.setOrder(Integer.MIN_VALUE + 100);
        log.info("已注册 ContextInitFilter (统一上下文管理)");
        return registration;
    }
} 