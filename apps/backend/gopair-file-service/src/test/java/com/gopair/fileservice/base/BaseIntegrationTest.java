package com.gopair.fileservice.base;

import com.gopair.common.service.WebSocketMessageProducer;
import com.gopair.fileservice.service.impl.FileServiceImpl;
import io.minio.MinioClient;
import org.junit.jupiter.api.AfterEach;
import org.mockito.Mockito;
import static org.mockito.Mockito.lenient;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

/**
 * 文件服务集成测试基础类。
 *
 * * [核心策略]
 * - 真实 MySQL + 真实 Redis：使用 gopair_test 数据库和 Redis，@Transactional 保证 DB 回滚。
 * - Redis 手动清理：@AfterEach 执行 FLUSHDB 清理 Redis 数据（Redis 不支持事务回滚）。
 * - MQ/MinIO/WebSocket Mock：避免测试间相互干扰，保证幂等性。
 * - Redis ValueOperations Mock：通过 Mockito.spy() 包装真实 StringRedisTemplate，
 *   使用 doReturn().when() 拦截 final 方法 opsForValue()，然后注入到 FileServiceImpl。
 *
 * @author gopair
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Transactional
public abstract class BaseIntegrationTest {

    /** 真实 Redis 连接，测试后通过 FLUSHDB 清理 */
    @Autowired
    protected StringRedisTemplate stringRedisTemplate;

    @MockBean
    protected MinioClient minioClient;

    @MockBean
    protected WebSocketMessageProducer webSocketMessageProducer;

    @MockBean
    protected ConnectionFactory connectionFactory;

    @MockBean
    protected RabbitTemplate rabbitTemplate;

    @LocalServerPort
    protected int port;

    @Autowired
    protected TestRestTemplate testRestTemplate;

    @Autowired
    protected FileServiceImpl fileService;

    /**
     * ValueOperations Mock，供子类注入使用。
     * 由 injectMockValueOperations() 初始化，通过 spy + doReturn 方式注入。
     */
    protected ValueOperations<String, String> valueOperations;

    /**
     * StringRedisTemplate Spy，供子类在 verify() 中使用。
     * injectMockValueOperations() 创建并注入后保存于此。
     */
    protected StringRedisTemplate redisTemplateSpy;

    /**
     * 每个测试方法结束后清理 Redis 数据。
     * MySQL 数据由 @Transactional 自动回滚，无需手动清理。
     */
    @AfterEach
    void flushTestRedis() {
        var factory = stringRedisTemplate.getConnectionFactory();
        if (factory != null && factory.getConnection() != null) {
            factory.getConnection().serverCommands().flushDb();
        }
    }

    /**
     * 创建并注入 Redis ValueOperations Mock。
     *
     * opsForValue() 是 final 方法，Mockito 无法在 when() 中拦截。
     * 解决：使用 Mockito.spy() 对真实 StringRedisTemplate 进行包装，
     * 对 spy 使用 doReturn().when() 设置 opsForValue() 返回 mock，
     * 然后通过 ReflectionTestUtils 将 spy 注入 FileServiceImpl 的 private redisTemplate 字段。
     */
    @SuppressWarnings("unchecked")
    protected void injectMockValueOperations() {
        valueOperations = Mockito.mock(ValueOperations.class);
        redisTemplateSpy = Mockito.spy(stringRedisTemplate);
        lenient().doReturn(valueOperations).when(redisTemplateSpy).opsForValue();
        ReflectionTestUtils.setField(fileService, "redisTemplate", redisTemplateSpy);
    }

    protected String getUrl(String path) {
        return "http://localhost:" + port + path;
    }

    protected String getBaseUrl() {
        return "http://localhost:" + port;
    }
}
