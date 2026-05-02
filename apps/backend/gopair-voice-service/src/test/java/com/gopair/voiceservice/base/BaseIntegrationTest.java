package com.gopair.voiceservice.base;

import com.gopair.common.service.WebSocketMessageProducer;
import com.gopair.voiceservice.mapper.VoiceCallMapper;
import com.gopair.voiceservice.mapper.VoiceCallParticipantMapper;
import com.gopair.voiceservice.messaging.RoomEventConsumer;
import org.junit.jupiter.api.AfterEach;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestTemplate;

/**
 * 语音通话服务集成测试基础类。
 *
 * * [核心策略]
 * - 真实 MySQL + 真实 Redis：使用 gopair_test 数据库和 Redis DB 14。
 * - Redis 手动清理：@AfterEach 执行 flushDb() 清理 Redis 数据（Redis 不支持事务回滚）。
 * - MQ/WebSocket Mock：MQ 消费者/生产者/WebSocket 均 Mock，避免测试间相互干扰。
 * - 事务管理：
 *   - JdbcTemplate（注入）：始终无事务，用于 @BeforeEach/@AfterEach 清理，不受 @Transactional 回滚影响。
 *   - TransactionTemplate：提供手动事务控制，供 Controller 测试在 HTTP 请求前提交数据。
 *   - Service 测试：不再使用 @Transactional，改用 newCallWithCommit() 创建并提交数据。
 *
 * @author gopair
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@MapperScan("com.gopair.voiceservice.mapper")
public abstract class BaseIntegrationTest {

    /** 真实 Redis 连接，测试后通过 flushDb() 清理 */
    @Autowired
    protected StringRedisTemplate stringRedisTemplate;

    /** 非事务性 DB 访问（用于 @BeforeEach 清理，不参与测试的 @Transactional 回滚） */
    @Autowired
    protected JdbcTemplate jdbcTemplate;

    /** 事务模板，用于在非事务测试中手动提交数据（如 Controller 层测试的数据准备） */
    @Autowired
    protected TransactionTemplate transactionTemplate;

    @MockBean
    protected ConnectionFactory connectionFactory;

    @MockBean
    protected RabbitTemplate rabbitTemplate;

    @MockBean
    protected WebSocketMessageProducer webSocketMessageProducer;

    @MockBean
    protected RoomEventConsumer roomEventConsumer;

    @LocalServerPort
    protected int port;

    @Autowired
    protected TestRestTemplate testRestTemplate;

    /** 每个测试方法结束后清理 Redis 数据 */
    @AfterEach
    protected void flushTestRedis() {
        var factory = stringRedisTemplate.getConnectionFactory();
        if (factory != null && factory.getConnection() != null) {
            factory.getConnection().serverCommands().flushDb();
        }
    }

    protected String getUrl(String path) {
        return "http://localhost:" + port + path;
    }

    /**
     * 通过 JdbcTemplate 直接查询数据库，绕过 MyBatis 一级缓存（L1）。
     * 用于在 Service 操作后验证 DB 实际状态。
     *
     * @param callId 通话 ID
     * @return Object[] 顺序为: call_id, room_id, initiator_id, call_type, status, start_time, end_time, duration, is_auto_created
     */
    protected Object[] selectCall(Long callId) {
        return jdbcTemplate.queryForObject(
                "SELECT call_id, room_id, initiator_id, call_type, status, start_time, end_time, duration, is_auto_created FROM voice_call WHERE call_id = ?",
                (rs, rowNum) -> new Object[]{
                        rs.getLong("call_id"),
                        rs.getLong("room_id"),
                        rs.getLong("initiator_id"),
                        rs.getInt("call_type"),
                        rs.getInt("status"),
                        rs.getTimestamp("start_time"),
                        rs.getTimestamp("end_time"),
                        rs.getObject("duration"),
                        rs.getBoolean("is_auto_created")
                },
                callId);
    }
}
