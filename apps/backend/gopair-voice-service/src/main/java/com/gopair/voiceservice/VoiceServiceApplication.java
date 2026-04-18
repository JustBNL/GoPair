package com.gopair.voiceservice;

import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import com.gopair.common.config.RabbitMQAutoConfiguration;

/**
 * 语音通话服务启动类
 *
 * @author gopair
 */
@Slf4j
@SpringBootApplication(exclude = {
        RabbitMQAutoConfiguration.class
})
@MapperScan("com.gopair.voiceservice.mapper")
@EnableDiscoveryClient
public class VoiceServiceApplication {

    public static void main(String[] args) {
        try {
            SpringApplication.run(VoiceServiceApplication.class, args);
            log.info("[语音服务启动] ========================================");
            log.info("[语音服务启动] GoPair 语音服务启动成功！");
            log.info("[语音服务启动] ========================================");
        } catch (Exception e) {
            log.error("[语音服务启动] 语音服务启动失败", e);
            System.exit(1);
        }
    }
}
