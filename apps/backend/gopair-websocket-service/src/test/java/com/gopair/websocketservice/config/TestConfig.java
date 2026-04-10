package com.gopair.websocketservice.config;

import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 模拟 Redis + RabbitMQ 依赖的测试配置。
 *
 * * [核心策略]
 * - RedisTemplate：使用内嵌 Map 模拟 Hash/Value/Set 操作，无需真实 Redis。
 * - RedisConnectionFactory：Mock，避免 Spring Data Redis 自动配置。
 * - RabbitTemplate / ConnectionFactory：Mock，避免 RabbitMQ 连接。
 *
 * * [已知限制]
 * - 仅覆盖实际被测试代码调用的方法，未调用的方法不做打桩。
 */
@TestConfiguration
public class TestConfig {

    // ==================== Redis 模拟 ====================

    /**
     * 模拟 RedisConnectionFactory（RedisConfig.redisTemplate 的依赖）。
     */
    @Bean
    @Primary
    public RedisConnectionFactory redisConnectionFactory() {
        return mock(RedisConnectionFactory.class);
    }

    /**
     * 模拟 RedisTemplate，所有操作走内存 Map。
     */
    @Bean
    @Primary
    @SuppressWarnings("unchecked")
    public RedisTemplate<String, Object> redisTemplate() {
        return MockRedisHelper.createMockRedisTemplate();
    }

    // ==================== RabbitMQ 模拟 ====================

    /**
     * 模拟 RabbitTemplate。
     */
    @Bean
    @Primary
    public RabbitTemplate rabbitTemplate() {
        RabbitTemplate mock = mock(RabbitTemplate.class);
        doNothing().when(mock).convertAndSend(anyString(), any(Object.class));
        doNothing().when(mock).convertAndSend(anyString(), anyString(), any(Object.class));
        return mock;
    }

    /**
     * 模拟 AMQP ConnectionFactory。
     */
    @Bean
    @Primary
    public ConnectionFactory amqpConnectionFactory() {
        return mock(ConnectionFactory.class);
    }
}
