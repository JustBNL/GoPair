package com.gopair.roomservice.base;

import com.gopair.common.service.WebSocketMessageProducer;
import com.gopair.roomservice.messaging.JoinRoomConsumer;
import com.gopair.roomservice.messaging.JoinRoomProducer;
import com.gopair.roomservice.messaging.LeaveRoomConsumer;
import com.gopair.roomservice.messaging.LeaveRoomProducer;
import com.gopair.roomservice.messaging.UserOfflineConsumer;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * 房间服务集成测试基础类
 *
 * 为集成测试提供 Spring Boot 测试环境和通用工具方法
 *
 * @author gopair
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Transactional
public abstract class BaseIntegrationTest {

    @MockBean
    protected StringRedisTemplate stringRedisTemplate;

    @MockBean
    protected JoinRoomProducer joinRoomProducer;

    @MockBean
    protected JoinRoomConsumer joinRoomConsumer;

    @MockBean
    protected LeaveRoomProducer leaveRoomProducer;

    @MockBean
    protected LeaveRoomConsumer leaveRoomConsumer;

    @MockBean
    protected UserOfflineConsumer userOfflineConsumer;

    @MockBean
    protected ConnectionFactory connectionFactory;

    @MockBean
    protected RabbitTemplate rabbitTemplate;

    @MockBean
    protected WebSocketMessageProducer webSocketMessageProducer;

    @Autowired
    protected TestRestTemplate restTemplate;

    @LocalServerPort
    protected int port;

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
