package com.gopair.fileservice.service;

import com.gopair.common.service.WebSocketMessageProducer;
import com.gopair.fileservice.mapper.RoomFileMapper;
import com.gopair.fileservice.service.impl.FileServiceImpl;
import io.minio.MinioClient;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * 文件服务集成测试 Mock 注入基类。
 *
 * * [核心策略]
 * - MinioConfig 已通过 @Profile("!test") 排除在测试环境外。
 * - FileServiceTestContextConfiguration 使用 @Primary @Bean 提供 Mock Bean（优先级高于任何生产 Bean）。
 * - 本类声明 @Autowired 字段，通过 Spring 注入 Mock Bean 给子类使用。
 * - 子类通过继承直接访问这些 Mock 字段，无需重复声明。
 * - @Transactional 保证每个测试方法结束后自动回滚。
 * - 此模式已在 gopair-room-service 经生产项目验证。
 *
 * @author gopair
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Transactional
@Import(FileServiceTestContextConfiguration.class)
public abstract class FileServiceIntegrationTestSupport {

    @Autowired
    protected RoomFileMapper roomFileMapper;

    @Autowired
    protected FileServiceImpl fileService;

    @Autowired
    protected MinioClient minioClient;

    @Autowired
    protected StringRedisTemplate stringRedisTemplate;

    @Autowired
    protected WebSocketMessageProducer webSocketMessageProducer;

    @Autowired
    protected ConnectionFactory connectionFactory;

    @Autowired
    protected RabbitTemplate rabbitTemplate;

    protected org.springframework.data.redis.core.ValueOperations<String, String> valueOperations;

    protected void setUpValueOperations() {
        valueOperations = org.mockito.Mockito.mock(
                org.springframework.data.redis.core.ValueOperations.class);
        org.mockito.Mockito.lenient()
                .when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        org.mockito.Mockito.lenient()
                .when(valueOperations.setIfAbsent(org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString())).thenReturn(true);
    }
}
