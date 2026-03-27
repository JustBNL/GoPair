package com.gopair.messageservice;

import com.gopair.messageservice.config.MessageProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 消息服务启动类
 * 
 * @author gopair
 */
@Slf4j
@SpringBootApplication(scanBasePackages = {
    "com.gopair.messageservice"
})
@EnableDiscoveryClient
@EnableConfigurationProperties(MessageProperties.class)
public class MessageServiceApplication {

    public static void main(String[] args) {
        try {
            SpringApplication.run(MessageServiceApplication.class, args);
            log.info("[消息服务启动] ========================================");
            log.info("[消息服务启动] GoPair 消息服务启动成功！");
            log.info("[消息服务启动] ========================================");
        } catch (Exception e) {
            log.error("[消息服务启动] 消息服务启动失败", e);
            System.exit(1);
        }
    }
}
