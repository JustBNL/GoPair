package com.gopair.roomservice;

import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;

/**
 * 房间服务启动类
 * 
 * @author gopair
 */
@Slf4j
@SpringBootApplication
@MapperScan("com.gopair.roomservice.mapper")
@EnableDiscoveryClient
@EnableScheduling
@EnableRabbit
public class RoomServiceApplication {

    public static void main(String[] args) {
        try {
            SpringApplication.run(RoomServiceApplication.class, args);
            log.info("[房间服务启动] ========================================");
            log.info("[房间服务启动] GoPair 房间服务启动成功！");
            log.info("[房间服务启动] ========================================");
        } catch (Exception e) {
            log.error("[房间服务启动] 房间服务启动失败", e);
            System.exit(1);
        }
    }
}
