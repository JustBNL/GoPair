package com.gopair.fileservice.service;

import com.gopair.fileservice.mapper.RoomFileMapper;
import com.gopair.fileservice.service.impl.FileServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * 文件服务集成测试 Mock 注入基类。
 *
 * * [核心策略]
 * - MinioConfig 已通过 @Profile("!test") 排除在测试环境外。
 * - FileServiceTestContextConfiguration 提供 Mock 版本的外部依赖（Minio / Redis / WebSocket / RabbitMQ）。
 * - 本类通过 @Autowired 获取注入的 Mock Bean，不再重复声明 @MockBean。
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
    protected io.minio.MinioClient minioClient;

    @Autowired
    protected org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;

    @Autowired
    protected com.gopair.common.service.WebSocketMessageProducer webSocketMessageProducer;
}
