package com.gopair.websocketservice.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Jackson配置类
 * 解决WebSocket服务中的序列化/反序列化问题
 * 
 * @author gopair
 */
@Slf4j
@Configuration
public class JacksonConfig {

    /**
     * 配置ObjectMapper Bean
     */
    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        
        // 添加Java 8时间类型支持
        mapper.registerModule(new JavaTimeModule());
        
        // 禁用时间戳格式（使用ISO格式字符串）
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        
        // 忽略未知属性
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        
        // 忽略空值
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        
        // 完全禁用多态类型处理（解决WRAPPER_ARRAY问题）
        mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        mapper.deactivateDefaultTyping();
        
        log.info("[WebSocket服务] Jackson ObjectMapper配置完成 - 已禁用类型包装器");
        
        return mapper;
    }
} 