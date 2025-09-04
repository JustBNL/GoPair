package com.gopair.websocketservice.config;

import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 简化的测试配置类
 * 提供模拟的Redis和RabbitMQ依赖
 * 
 * @author gopair
 */
@TestConfiguration
public class TestConfig {

    /**
     * 模拟RedisTemplate，使用内存存储
     */
    @Bean
    @Primary
    @SuppressWarnings("unchecked")
    public RedisTemplate<String, Object> redisTemplate() {
        Map<String, Object> memoryStore = new ConcurrentHashMap<>();
        
        RedisTemplate<String, Object> mockRedisTemplate = mock(RedisTemplate.class);
        HashOperations<String, Object, Object> mockHashOps = mock(HashOperations.class);
        ValueOperations<String, Object> mockValueOps = mock(ValueOperations.class);
        
        // 模拟HashOperations
        when(mockRedisTemplate.opsForHash()).thenReturn(mockHashOps);
        doAnswer(invocation -> {
            String key = invocation.getArgument(0);
            Map<String, Object> hashValues = (Map<String, Object>) invocation.getArgument(2);
            memoryStore.put(key + "_hash", hashValues);
            return null;
        }).when(mockHashOps).putAll(anyString(), any(Map.class));
        
        doAnswer(invocation -> {
            String key = invocation.getArgument(0);
            Object hashKey = invocation.getArgument(1);
            Object value = invocation.getArgument(2);
            String fullKey = key + "_hash_" + hashKey;
            memoryStore.put(fullKey, value);
            return null;
        }).when(mockHashOps).put(anyString(), any(), any());
        
        doAnswer(invocation -> {
            String key = invocation.getArgument(0);
            return memoryStore.getOrDefault(key + "_hash", new ConcurrentHashMap<>());
        }).when(mockHashOps).entries(anyString());
        
        // 模拟ValueOperations
        when(mockRedisTemplate.opsForValue()).thenReturn(mockValueOps);
        doAnswer(invocation -> {
            String key = invocation.getArgument(0);
            Object value = invocation.getArgument(1);
            memoryStore.put(key, value);
            return null;
        }).when(mockValueOps).set(anyString(), any());
        
        doAnswer(invocation -> {
            String key = invocation.getArgument(0);
            return memoryStore.get(key);
        }).when(mockValueOps).get(anyString());
        
        // 模拟基本操作
        when(mockRedisTemplate.expire(anyString(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        
        doAnswer(invocation -> {
            String pattern = invocation.getArgument(0);
            return memoryStore.keySet().stream()
                .filter(key -> key.contains(pattern.replace("*", "")))
                .collect(java.util.stream.Collectors.toSet());
        }).when(mockRedisTemplate).keys(anyString());
        
        when(mockRedisTemplate.delete(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            return memoryStore.remove(key) != null;
        });
        
        return mockRedisTemplate;
    }

    /**
     * 模拟RabbitTemplate
     */
    @Bean
    @Primary
    public RabbitTemplate rabbitTemplate() {
        RabbitTemplate mockRabbitTemplate = mock(RabbitTemplate.class);
        doNothing().when(mockRabbitTemplate).convertAndSend(anyString(), any(Object.class));
        doNothing().when(mockRabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));
        return mockRabbitTemplate;
    }

    /**
     * 模拟ConnectionFactory
     */
    @Bean
    @Primary
    public ConnectionFactory connectionFactory() {
        return mock(ConnectionFactory.class);
    }
} 