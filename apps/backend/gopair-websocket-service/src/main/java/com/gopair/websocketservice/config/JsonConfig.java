package com.gopair.websocketservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * JSON配置类 - 提供全局统一的ObjectMapper
 * 
 * @author gopair
 */
@Configuration
public class JsonConfig {

    /**
     * 创建全局ObjectMapper Bean
     * 使用@Primary确保优先使用这个实例
     */
    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
} 