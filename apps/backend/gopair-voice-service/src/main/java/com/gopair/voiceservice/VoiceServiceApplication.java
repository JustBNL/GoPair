package com.gopair.voiceservice;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 语音通话服务启动类
 *
 * @author gopair
 */
@SpringBootApplication
@MapperScan("com.gopair.voiceservice.mapper")
@EnableDiscoveryClient
public class VoiceServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(VoiceServiceApplication.class, args);
    }
}
