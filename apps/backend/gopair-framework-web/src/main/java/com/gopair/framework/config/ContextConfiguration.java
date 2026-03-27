package com.gopair.framework.config;

import com.gopair.framework.config.properties.ContextProperties;
import com.gopair.framework.context.ContextInitFilter;
import com.gopair.framework.context.TraceContextSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.Nullable;

import jakarta.servlet.Filter;

/**
 * 上下文功能配置类
 *
 * @author gopair
 */
@Slf4j
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(Filter.class)
@ConditionalOnProperty(prefix = "gopair.context", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(ContextProperties.class)
public class ContextConfiguration {

    @Bean
    public ContextInitFilter contextInitFilter(
            ContextProperties properties,
            @Autowired(required = false) @Nullable TraceContextSupport traceContextSupport) {
        return new ContextInitFilter(properties, traceContextSupport);
    }

    @Bean
    public FilterRegistrationBean<ContextInitFilter> contextInitFilterRegistration(ContextInitFilter filter) {
        FilterRegistrationBean<ContextInitFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(filter);
        registration.addUrlPatterns("/*");
        registration.setName("contextInitFilter");
        // 与类上 @Order(Integer.MIN_VALUE + 100) 对齐
        registration.setOrder(Integer.MIN_VALUE + 100);
        log.info("[框架配置] 已注册 ContextInitFilter");
        return registration;
    }
}
