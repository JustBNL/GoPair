package com.gopair.websocketservice.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import jakarta.annotation.PostConstruct;

/**
 * Redis配置类
 * 配置WebSocket服务的Redis连接和序列化
 * 
 * @author gopair
 */
@Slf4j
@Configuration
public class RedisConfig {

    @PostConstruct
    public void init() {
        log.info("[WebSocket服务] Redis配置初始化完成");
    }

    /**
     * 配置Redis专用的ObjectMapper Bean
     * 激活默认类型处理以支持Redis序列化中的多态对象
     * 
     * @return Redis专用ObjectMapper
     */
    @Bean
    @Qualifier("redisObjectMapper")
    public ObjectMapper redisObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        
        // 添加Java 8时间类型支持
        mapper.registerModule(new JavaTimeModule());
        
        // 禁用时间戳格式（使用ISO格式字符串）
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        
        // 忽略未知属性
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        
        // 忽略空值
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        
        // 为Redis序列化激活多态类型处理
        mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        mapper.activateDefaultTyping(mapper.getPolymorphicTypeValidator(), ObjectMapper.DefaultTyping.NON_FINAL);
        
        log.info("[WebSocket服务] Redis专用ObjectMapper配置完成 - 已激活类型包装器");
        
        return mapper;
    }

    /**
     * 配置RedisTemplate
     * 使用专用的Redis ObjectMapper，避免与WebSocket消息处理的ObjectMapper配置冲突
     * 
     * @param connectionFactory Redis连接工厂
     * @param redisObjectMapper Redis专用ObjectMapper
     * @return RedisTemplate
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory, 
                                                        @Qualifier("redisObjectMapper") ObjectMapper redisObjectMapper) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // 使用Jackson2JsonRedisSerializer序列化和反序列化Redis的value值
        // 直接使用专用的redisObjectMapper，该ObjectMapper已经配置了类型处理
        Jackson2JsonRedisSerializer<Object> jackson2JsonRedisSerializer = new Jackson2JsonRedisSerializer<>(Object.class);
        jackson2JsonRedisSerializer.setObjectMapper(redisObjectMapper);

        // 使用StringRedisSerializer序列化和反序列化Redis的key值
        StringRedisSerializer stringRedisSerializer = new StringRedisSerializer();

        // 设置序列化器
        template.setKeySerializer(stringRedisSerializer);
        template.setValueSerializer(jackson2JsonRedisSerializer);
        template.setHashKeySerializer(stringRedisSerializer);
        template.setHashValueSerializer(jackson2JsonRedisSerializer);

        template.afterPropertiesSet();
        
        log.info("[WebSocket服务] RedisTemplate配置完成 - 使用专用Redis ObjectMapper");
        return template;
    }
} 