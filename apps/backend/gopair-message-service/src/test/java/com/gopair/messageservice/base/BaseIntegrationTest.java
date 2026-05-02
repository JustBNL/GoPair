package com.gopair.messageservice.base;

import com.gopair.common.service.WebSocketMessageProducer;
import com.gopair.framework.config.FrameworkAutoConfiguration;
import com.gopair.messageservice.config.MockRestTemplateConfig;
import com.gopair.messageservice.service.MessageService;
import com.gopair.messageservice.service.UserProfileFallbackService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

/**
 * 消息服务集成测试基础类。
 *
 * * [核心策略]
 * - 真实 MySQL + 真实 Redis：使用 gopair_test 数据库和 Redis DB 14，@Transactional 保证 DB 回滚。
 * - Redis 手动清理：@AfterEach 执行 flushDb() 清理 Redis 数据（Redis 不支持事务回滚）。
 * - MQ/WebSocket Mock：RabbitMQ、WebSocket 推送均通过 @MockBean Mock，避免测试间相互干扰。
 * - RestTemplate 双策略：mockRestTemplate（@Primary）拦截外部服务调用，realRestTemplate 供 Controller 测试走 localhost。
 *
 * @author gopair
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Transactional
@Import({FrameworkAutoConfiguration.class, MockRestTemplateConfig.class})
@MapperScan({"com.gopair.messageservice.mapper"})
public abstract class BaseIntegrationTest {

    /** 真实 Redis 连接，测试后通过 flushDb() 清理 */
    @Autowired
    protected StringRedisTemplate stringRedisTemplate;

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

    /**
     * 直接注入 mock RestTemplate（由 MockRestTemplateConfig 提供），
     * 而非使用 TestRestTemplate 内部的 RestTemplate。
     */
    @Autowired
    @Qualifier("realRestTemplate")
    protected RestTemplate realRestTemplate;

    @Autowired
    @Qualifier("mockRestTemplate")
    protected RestTemplate mockRestTemplate;

    @Autowired
    protected MessageService messageService;

    @Autowired
    protected UserProfileFallbackService userProfileFallbackService;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

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

    protected String getUrl(String path) {
        return "http://localhost:" + port + path;
    }

    /**
     * 模拟用户是否在房间内
     *
     * @param roomId   房间ID
     * @param userId   用户ID
     * @param isMember 是否在房间内（true=在，false=不在）
     */
    protected void mockUserInRoom(Long roomId, Long userId, boolean isMember) {
        String url = "http://room-service/room/" + roomId + "/members/" + userId + "/check";
        MockRestTemplateConfig.putStub(url, isMember);
    }
}
