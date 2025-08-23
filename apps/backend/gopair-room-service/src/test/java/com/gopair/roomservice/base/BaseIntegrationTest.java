package com.gopair.roomservice.base;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * 房间服务集成测试基础类
 * 
 * 为集成测试提供Spring Boot测试环境和通用工具方法
 * 测试HTTP → Controller → Service → Repository → Database 完整链路
 * 
 * @author gopair
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Transactional
public abstract class BaseIntegrationTest {

    /**
     * Spring Boot Test提供的HTTP客户端
     * 用于发送真实的HTTP请求测试完整链路
     */
    @Autowired
    protected TestRestTemplate restTemplate;

    /**
     * 测试服务器的随机端口
     * Spring Boot会自动分配一个可用端口
     */
    @LocalServerPort
    protected int port;

    /**
     * 构建完整的API URL
     * 
     * @param path API路径，如 "/room" 或 "/room/join"
     * @return 完整的URL，如 "http://localhost:8082/room"
     */
    protected String getUrl(String path) {
        return "http://localhost:" + port + path;
    }

    /**
     * 构建根URL
     * 
     * @return 服务器根URL，如 "http://localhost:8082"
     */
    protected String getBaseUrl() {
        return "http://localhost:" + port;
    }

    /**
     * 构建房间API URL
     * 
     * @param path 房间API路径，如 "" 或 "/join"
     * @return 房间API URL，如 "http://localhost:8082/room"
     */
    protected String getRoomUrl(String path) {
        return getUrl("/room" + path);
    }
} 