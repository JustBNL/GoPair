package com.gopair.userservice;

import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.gopair.userservice.config.JwtConfig;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 用户服务启动类
 * 
 * @author gopair
 */
@Slf4j
@SpringBootApplication
@MapperScan("com.gopair.userservice.mapper")
@EnableConfigurationProperties(JwtConfig.class)
@EnableDiscoveryClient
public class UserServiceApplication {

    public static void main(String[] args) {
        try {
            SpringApplication.run(UserServiceApplication.class, args);
            log.info("[用户服务启动] ========================================");
            log.info("[用户服务启动] GoPair 用户服务启动成功！");
            log.info("[用户服务启动] ========================================");
        } catch (Exception e) {
            log.error("[用户服务启动] 用户服务启动失败", e);
            System.exit(1);
        }
    }

}
