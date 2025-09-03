package com.gopair.websocketservice;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * WebSocket统一服务启动类
 * 
 * @author gopair
 */
@Slf4j
@SpringBootApplication(exclude = {
    DataSourceAutoConfiguration.class,
    HibernateJpaAutoConfiguration.class
})
@EnableDiscoveryClient
public class WebSocketServiceApplication {

    public static void main(String[] args) {
        try {
            SpringApplication.run(WebSocketServiceApplication.class, args);
            log.info("[WebSocket服务启动] ========================================");
            log.info("[WebSocket服务启动] GoPair WebSocket服务启动成功！");
            log.info("[WebSocket服务启动] ========================================");
        } catch (Exception e) {
            log.error("[WebSocket服务启动] WebSocket服务启动失败", e);
            System.exit(1);
        }
    }
} 