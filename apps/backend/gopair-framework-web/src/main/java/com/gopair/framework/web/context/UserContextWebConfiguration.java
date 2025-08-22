package com.gopair.framework.web.context;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 用户上下文Web配置类
 * 
 * 自动配置用户上下文拦截器，提供用户信息的ThreadLocal管理功能
 * 
 * @author gopair
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(UserContextProperties.class)
@ConditionalOnProperty(name = "gopair.user-context.enabled", havingValue = "true", matchIfMissing = true)
public class UserContextWebConfiguration implements WebMvcConfigurer {

    private final UserContextProperties properties;

    public UserContextWebConfiguration(UserContextProperties properties) {
        this.properties = properties;
        log.info("用户上下文配置已启用: enabled={}, logEnabled={}", 
                 properties.isEnabled(), properties.isLogEnabled());
    }

    /**
     * 创建用户上下文拦截器Bean
     * 
     * @return 用户上下文拦截器实例
     */
    @Bean
    public UserContextInterceptor userContextInterceptor() {
        return new UserContextInterceptor();
    }

    /**
     * 添加拦截器配置
     * 
     * @param registry 拦截器注册器
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(userContextInterceptor())
                .addPathPatterns("/**")
                .excludePathPatterns(properties.getExcludedPaths().toArray(new String[0]));
                
        log.info("用户上下文拦截器已注册，排除路径: {}", properties.getExcludedPaths());
    }
} 