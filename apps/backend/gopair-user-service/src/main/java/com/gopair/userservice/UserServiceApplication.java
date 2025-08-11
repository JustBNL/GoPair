package com.gopair.userservice;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.gopair.userservice.config.JwtConfig;

/**
 * 用户服务启动类
 * 
 * @author gopair
 */
@SpringBootApplication
@MapperScan("com.gopair.userservice.mapper")
@EnableConfigurationProperties(JwtConfig.class)
public class UserServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }

}
