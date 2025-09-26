package com.gopair.roomservice;

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
@SpringBootApplication
@MapperScan("com.gopair.roomservice.mapper")
@EnableDiscoveryClient
@EnableScheduling
@EnableRabbit
public class RoomServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(RoomServiceApplication.class, args);
    }
} 