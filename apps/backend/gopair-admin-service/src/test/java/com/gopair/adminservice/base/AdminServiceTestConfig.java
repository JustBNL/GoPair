package com.gopair.adminservice.base;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.task.SyncTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Admin Service 测试 Mock 配置。
 *
 * * [核心策略]
 * - PasswordEncoder：使用真实 BCryptPasswordEncoder 实例，
 *   支持 AdminAuthService.login() 中的 matches() 调用。
 * - Executor (adminAuditTaskExecutor)：注入 SyncTaskExecutor，
 *   将 Aspect 中的异步日志写入变为同步执行，保证测试可确定性等待日志落库。
 * - AdminAuditLogMapper：使用真实 Mapper，由 Spring Boot 通过 @SpringBootTest
 *   自动装配（H2 数据库），集成测试中真实写入审计日志。
 *
 * @author gopair
 */
@TestConfiguration
public class AdminServiceTestConfig {

    @Bean
    @Primary
    public org.springframework.security.crypto.password.PasswordEncoder passwordEncoder() {
        return new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
    }

    @Bean("adminAuditTaskExecutor")
    public Executor syncTaskExecutor() {
        return new SyncTaskExecutor();
    }
}
