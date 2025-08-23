package com.gopair.roomservice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 定时任务配置
 * 
 * @author gopair
 */
@Configuration
@EnableScheduling
public class ScheduleConfig {
    // Spring Boot会自动配置TaskScheduler
} 