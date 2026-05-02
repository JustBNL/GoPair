package com.gopair.chatservice;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@Slf4j
@SpringBootApplication(scanBasePackages = {"com.gopair.chatservice"})
@EnableDiscoveryClient
public class ChatServiceApplication {

    public static void main(String[] args) {
        try {
            SpringApplication.run(ChatServiceApplication.class, args);
            log.info("[聊天服务] GoPair 聊天服务启动成功！");
        } catch (Exception e) {
            log.error("[聊天服务] 聊天服务启动失败", e);
            System.exit(1);
        }
    }
}
