package com.gopair.fileservice.service;

import com.gopair.common.service.WebSocketMessageProducer;
import io.minio.MinioClient;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 文件服务集成测试 Mock 配置。
 *
 * 使用 @Primary @Bean 提供 Mock Bean，优先级高于任何生产 Bean。
 * MinioConfig 已通过 @Profile("!test") 排除在测试环境外。
 *
 * @author gopair
 */
@TestConfiguration
public class FileServiceTestContextConfiguration {

    @Bean
    @Primary
    public MinioClient minioClient() {
        return org.mockito.Mockito.mock(MinioClient.class,
                org.mockito.Answers.RETURNS_DEFAULTS);
    }

    @Bean
    @Primary
    public StringRedisTemplate stringRedisTemplate() {
        return org.mockito.Mockito.mock(StringRedisTemplate.class);
    }

    @Bean
    @Primary
    public WebSocketMessageProducer webSocketMessageProducer() {
        return org.mockito.Mockito.mock(WebSocketMessageProducer.class);
    }

    @Bean
    @Primary
    public ConnectionFactory connectionFactory() {
        return org.mockito.Mockito.mock(ConnectionFactory.class);
    }

    @Bean
    @Primary
    public RabbitTemplate rabbitTemplate() {
        return org.mockito.Mockito.mock(RabbitTemplate.class);
    }
}
