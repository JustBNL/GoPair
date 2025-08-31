package com.gopair.roomservice.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import jakarta.annotation.PostConstruct;

/**
 * 定时任务配置
 * 
 * @author gopair
 */
@Slf4j
@Configuration
@EnableScheduling
public class ScheduleConfig {

    @PostConstruct
    public void init() {
        log.info("[房间服务] 定时任务配置初始化完成");
    }
    
    // Spring Boot会自动配置TaskScheduler
} 