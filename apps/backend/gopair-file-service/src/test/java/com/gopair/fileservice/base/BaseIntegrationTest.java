package com.gopair.fileservice.base;

import com.gopair.common.service.WebSocketMessageProducer;
import io.minio.MinioClient;
import org.mockito.Mockito;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 文件服务集成测试 Mock 配置。
 *
 * 所有 Mock Bean 在这里集中声明，用 @Primary 确保覆盖生产 Bean。
 *
 * @author gopair
 */
@TestConfiguration
public class BaseIntegrationTest {

    @Bean
    @Primary
    public MinioClient minioClient() {
        return Mockito.mock(MinioClient.class);
    }

    @Bean
    @Primary
    public StringRedisTemplate stringRedisTemplate() {
        return Mockito.mock(StringRedisTemplate.class);
    }

    @Bean
    @Primary
    public WebSocketMessageProducer webSocketMessageProducer() {
        return Mockito.mock(WebSocketMessageProducer.class);
    }

    @Bean
    @Primary
    public ConnectionFactory connectionFactory() {
        return Mockito.mock(ConnectionFactory.class);
    }

    @Bean
    @Primary
    public RabbitTemplate rabbitTemplate() {
        return Mockito.mock(RabbitTemplate.class);
    }
}
