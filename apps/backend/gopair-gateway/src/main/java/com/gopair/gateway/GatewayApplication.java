package com.gopair.gateway;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import reactor.core.publisher.Hooks;

/**
 * 网关服务启动类
 * 
 * 基于Spring Cloud Gateway的响应式网关服务
 * 
 * @author gopair
 */
@SpringBootApplication(
    exclude = {
        org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration.class,
        org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration.class,
        org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration.class
    }
)
@EnableDiscoveryClient
public class GatewayApplication {

    @PostConstruct
    public void init() {
        // 启用自动context传播 - 这是Spring Boot 3 + Micrometer的标准方式
        Hooks.enableAutomaticContextPropagation();
    }

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
} 