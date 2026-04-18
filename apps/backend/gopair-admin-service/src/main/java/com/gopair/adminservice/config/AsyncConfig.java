package com.gopair.adminservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 异步任务执行器配置。
 *
 * * [核心策略]
 * - adminAuditTaskExecutor：专供 AdminAuditAspect 使用，默认核心线程数 2，最大线程数 10。
 * - 命名 "adminAuditTaskExecutor"，与 Aspect 中 @Qualifier("adminAuditTaskExecutor") 对应。
 */
@Configuration
public class AsyncConfig {

    @Bean("adminAuditTaskExecutor")
    public Executor adminAuditTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("adminAudit-");
        executor.initialize();
        return executor;
    }
}
