package com.gopair.fileservice.service;

import com.gopair.common.service.WebSocketMessageProducer;
import com.gopair.fileservice.mapper.RoomFileMapper;
import com.gopair.fileservice.service.impl.FileServiceImpl;
import io.minio.MinioClient;
import org.mockito.Mockito;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.mockito.Mockito.withSettings;

/**
 * 文件服务集成测试上下文配置。
 *
 * * [核心策略]
 * - MinioConfig 已通过 @Profile("!test") 排除在测试环境外。
 * - 本配置提供 Mock 版本的外部依赖（Minio / Redis / WebSocket / RabbitMQ）。
 * - MinioClient 使用 RETURNS_MOCKS 设置，确保非 stub 方法不抛异常。
 *
 * @author gopair
 */
@TestConfiguration
public class FileServiceTestContextConfiguration {

    @Bean
    public MinioClient minioClient() {
        // RETURNS_MOCKS: void 方法不抛异常, 非 void 方法返回 mock 对象（而非 null）
        return Mockito.mock(MinioClient.class, withSettings()
                .defaultAnswer(org.mockito.Answers.RETURNS_MOCKS)
                .name("TestMinioClient"));
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate() {
        return Mockito.mock(StringRedisTemplate.class);
    }

    @Bean
    public WebSocketMessageProducer webSocketMessageProducer() {
        return Mockito.mock(WebSocketMessageProducer.class);
    }

    @Bean
    public ConnectionFactory connectionFactory() {
        return Mockito.mock(ConnectionFactory.class);
    }

    @Bean
    public RabbitTemplate rabbitTemplate() {
        return Mockito.mock(RabbitTemplate.class);
    }
}
