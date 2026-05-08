package com.gopair.roomservice;

import com.gopair.common.service.WebSocketMessageProducer;
import com.gopair.roomservice.config.RoomConfig;
import com.gopair.roomservice.messaging.JoinRoomConsumer;
import com.gopair.roomservice.messaging.JoinRoomProducer;
import com.gopair.roomservice.messaging.MemberRemovalConsumer;
import com.gopair.roomservice.messaging.MemberRemovalProducer;
import com.gopair.roomservice.messaging.UserOfflineConsumer;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 房间服务应用测试
 *
 * 验证 Spring 上下文能够正常加载
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class RoomServiceApplicationTests {

    @MockBean
    private StringRedisTemplate stringRedisTemplate;

    @MockBean
    private JoinRoomProducer joinRoomProducer;

    @MockBean
    private JoinRoomConsumer joinRoomConsumer;

    @MockBean
    private MemberRemovalProducer memberRemovalProducer;

    @MockBean
    private MemberRemovalConsumer memberRemovalConsumer;

    @MockBean
    private UserOfflineConsumer userOfflineConsumer;

    @MockBean
    private ConnectionFactory connectionFactory;

    @MockBean
    private RabbitTemplate rabbitTemplate;

    @MockBean
    private WebSocketMessageProducer webSocketMessageProducer;

    @Autowired(required = false)
    private RoomConfig roomConfig;

    @Test
    void contextLoads() {
        assertNotNull(roomConfig, "RoomConfig should be loaded");
    }
}
