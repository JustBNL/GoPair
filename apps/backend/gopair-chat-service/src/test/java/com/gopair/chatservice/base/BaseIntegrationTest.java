package com.gopair.chatservice.base;

import com.gopair.chatservice.config.ChatWebSocketProducer;
import com.gopair.framework.config.FrameworkAutoConfiguration;
import com.gopair.chatservice.config.MockRestTemplateConfig;
import com.gopair.chatservice.service.FriendService;
import com.gopair.chatservice.service.PrivateMessageService;
import com.gopair.chatservice.service.UserProfileFallbackService;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

/**
 * 聊天服务集成测试基础类。
 *
 * * [核心策略]
 * - 真实 MySQL + 真实 Redis：使用 gopair_test 数据库和 Redis DB 14，@Transactional 保证 DB 回滚。
 * - Redis 手动清理：@AfterEach 执行 flushDb() 清理 Redis 数据。
 * - RabbitMQ/WebSocket Mock：@MockBean Mock，避免测试间相互干扰。
 * - RestTemplate 双策略：mockRestTemplate（@Primary）拦截外部服务调用，realRestTemplate 供 Controller 测试。
 *
 * @author gopair
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Transactional
@Import({FrameworkAutoConfiguration.class, MockRestTemplateConfig.class})
@MapperScan({"com.gopair.chatservice.mapper"})
@TestPropertySource(properties = {
    "spring.sql.init.schema-locations=classpath:schema.sql"
})
public abstract class BaseIntegrationTest {

    @Autowired
    protected StringRedisTemplate stringRedisTemplate;

    @MockBean
    protected ChatWebSocketProducer chatWebSocketProducer;

    @MockBean
    protected ConnectionFactory connectionFactory;

    @MockBean
    protected RabbitTemplate rabbitTemplate;

    @LocalServerPort
    protected int port;

    @Autowired
    protected TestRestTemplate testRestTemplate;

    @Autowired
    @Qualifier("realRestTemplate")
    protected RestTemplate realRestTemplate;

    @Autowired
    @Qualifier("mockRestTemplate")
    protected RestTemplate mockRestTemplate;

    @Autowired
    protected FriendService friendService;

    @Autowired
    protected PrivateMessageService privateMessageService;

    @Autowired
    protected UserProfileFallbackService userProfileFallbackService;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @AfterEach
    void flushTestRedis() {
        var factory = stringRedisTemplate.getConnectionFactory();
        if (factory != null && factory.getConnection() != null) {
            factory.getConnection().serverCommands().flushDb();
        }
        MockRestTemplateConfig.clear();
    }

    protected String getUrl(String path) {
        return "http://localhost:" + port + path;
    }

    /**
     * 模拟用户资料获取。
     */
    protected void mockUserProfile(Long userId, String nickname, String avatar) {
        String url = "http://user-service/user/" + userId;
        MockRestTemplateConfig.putHttpStub(url,
            "{\"code\":200,\"data\":{\"userId\":" + userId + ",\"nickname\":\"" + nickname + "\",\"avatar\":\"" + avatar + "\"}}");
    }
}
