package com.gopair.userservice.base;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.junit.jupiter.api.AfterEach;

/**
 * 集成测试基础类
 *
 * * [核心策略]
 * - MySQL 事务回滚：@Transactional 注解确保每个测试方法结束后自动回滚。
 * - Redis 清理：Redis 不支持事务回滚，在 @AfterEach 中执行 flushDb() 清空当前 DB。
 *
 * * [执行链路]
 * 1. Spring Boot 启动完整上下文（@SpringBootTest RANDOM_PORT）。
 * 2. 子类注入 TestRestTemplate 发送真实 HTTP 请求，或直接注入 Service/Mapper 测试。
 * 3. 每个测试方法结束后：
 *    - MySQL：由 @Transactional 自动回滚，无需干预。
 *    - Redis：执行 flushDb() 清空当前 DB（database=15）。
 *
 * @author gopair
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Transactional
public abstract class BaseIntegrationTest {

	@Autowired
	protected TestRestTemplate restTemplate;

	@LocalServerPort
	protected int port;

	@Autowired
	private StringRedisTemplate redisTemplate;

	protected String getUrl(String path) {
		return "http://localhost:" + port + path;
	}

	protected String getBaseUrl() {
		return "http://localhost:" + port;
	}

	@AfterEach
	void cleanUpRedis() {
		var factory = redisTemplate.getConnectionFactory();
		if (factory != null) {
			var conn = factory.getConnection();
			if (conn != null) {
				conn.serverCommands().flushDb();
			}
		}
	}
} 