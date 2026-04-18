package com.gopair.voiceservice.base;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

/**
 * 语音通话服务集成测试基础类。
 *
 * <p>提供 Spring Boot 测试环境，所有测试需在 @AfterEach 中手动清理脏数据。
 * 不使用 @Transactional 避免测试线程与服务线程之间的事务可见性问题。
 * - 测试线程：直接通过 mapper/service 写 DB
 * - 服务线程：通过 HTTP 请求访问服务
 * - 两线程之间需要数据已提交才能互相可见
 *
 * @author gopair
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {

    @LocalServerPort
    protected int port;

    /**
     * Real RestTemplate：用于测试代码向 localhost 发送 HTTP 请求，
     * 走真实网络连接，确保 Controller 请求能正确到达应用。
     */
    @Autowired(required = false)
    @Qualifier("realRestTemplate")
    protected RestTemplate realRestTemplate;

    protected String getUrl(String path) {
        return "http://localhost:" + port + path;
    }
}
