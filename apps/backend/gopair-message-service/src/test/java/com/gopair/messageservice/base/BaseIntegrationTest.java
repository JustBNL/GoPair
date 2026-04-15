package com.gopair.messageservice.base;

import com.gopair.common.service.WebSocketMessageProducer;
import com.gopair.framework.config.FrameworkAutoConfiguration;
import com.gopair.messageservice.config.MockRestTemplateConfig;
import com.gopair.messageservice.service.MessageService;
import com.gopair.messageservice.service.UserProfileFallbackService;
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
 * 消息服务集成测试基础类
 *
 * 为集成测试提供 Spring Boot 测试环境、自动回滚和外部依赖 Mock
 *
 * * [RestTemplate Mock 策略]
 *   - mockRestTemplate（@Primary）：Service 层调用外部服务（room-service/user-service）时使用，
 *     通过 ConfigurableMockInterceptor 返回预设 stub，不走真实网络。
 *   - realRestTemplate（无 @Primary）：Controller 测试向 localhost 发送 HTTP 请求时使用，
 *     走真实网络连接，由 BaseIntegrationTest.realRestTemplate 提供。
 *   - 子类通过 mockUserInRoom(roomId, userId, true) 配置 mock 行为。
 *
 * @author gopair
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Transactional
@Import({FrameworkAutoConfiguration.class, MockRestTemplateConfig.class})
@MapperScan({"com.gopair.messageservice.mapper"})
public abstract class BaseIntegrationTest {

    @MockBean
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
