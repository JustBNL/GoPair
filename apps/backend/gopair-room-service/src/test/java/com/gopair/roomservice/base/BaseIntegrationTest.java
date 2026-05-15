package com.gopair.roomservice.base;

import com.gopair.common.service.WebSocketMessageProducer;
import com.gopair.roomservice.messaging.JoinRoomConsumer;
import com.gopair.roomservice.messaging.JoinRoomProducer;
import com.gopair.roomservice.messaging.MemberRemovalConsumer;
import com.gopair.roomservice.messaging.MemberRemovalProducer;
import com.gopair.roomservice.messaging.UserOfflineConsumer;
import org.junit.jupiter.api.AfterEach;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * 房间服务集成测试基础类。
 *
 * * [核心策略]
 * - 真实 MySQL + 真实 Redis：使用 gopair_test 数据库和 Redis DB 14，@Transactional 保证 DB 回滚。
 * - Redis 手动清理：@AfterEach 执行 FLUSHDB 清理 Redis 数据（Redis 不支持事务回滚）。
 * - MQ/WebSocket Mock：MQ 消费者/生产者/WebSocket 均 Mock，避免测试间相互干扰。
 * - Lua 脚本走真实 Redis：JoinReservationService / MemberRemovalService 使用 RedisConfig 中
 *   配置的真实 Lua 脚本，测试前确保 Redis 可用，测试后 flushDb() 清空。
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
    protected JoinRoomProducer joinRoomProducer;

    @MockBean
    protected JoinRoomConsumer joinRoomConsumer;

    @MockBean
    protected MemberRemovalProducer memberRemovalProducer;

    @MockBean
    protected MemberRemovalConsumer memberRemovalConsumer;

    @MockBean
    protected UserOfflineConsumer userOfflineConsumer;

    @MockBean
    protected ConnectionFactory connectionFactory;

    @MockBean
    protected RabbitTemplate rabbitTemplate;

    @MockBean
    protected WebSocketMessageProducer webSocketMessageProducer;

    @LocalServerPort
    protected int port;

    @Autowired
    protected TestRestTemplate testRestTemplate;

    @Autowired
    protected PlatformTransactionManager transactionManager;

    /**
     * TransactionTemplate with REQUIRES_NEW propagation.
     * Creates an independent committed transaction, bypassing the test class's @Transactional rollback.
     * Use to execute Redis init before the test class tx commits.
     */
    protected TransactionTemplate createRequiresNewTxTemplate() {
        org.springframework.transaction.support.DefaultTransactionDefinition def =
                new org.springframework.transaction.support.DefaultTransactionDefinition();
        def.setPropagationBehavior(org.springframework.transaction.annotation.Propagation.REQUIRES_NEW.value());
        return new TransactionTemplate(transactionManager, def);
    }

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

    protected String getBaseUrl() {
        return "http://localhost:" + port;
    }

    protected String getRoomUrl(String path) {
        return getUrl("/room" + path);
    }
}
