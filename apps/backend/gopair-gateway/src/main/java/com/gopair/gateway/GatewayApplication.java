package com.gopair.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 网关服务启动类
 * 
 * 基于Spring Cloud Gateway的响应式网关服务
 * 
 * @author gopair
 */
@SpringBootApplication(
    scanBasePackages = {"com.gopair.gateway", "com.gopair.common"},
    exclude = {
        // 排除数据库相关自动配置（网关不需要数据库）
        org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration.class,
        org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration.class,
        // 排除MVC相关自动配置（网关使用WebFlux）
        org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration.class,
        org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class,
        org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration.class
    }
)
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
} 