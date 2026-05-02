package com.gopair.adminservice.base;

import com.gopair.adminservice.context.AdminContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin Service 集成测试基类。
 *
 * * [核心策略]
 * - @Transactional：所有测试方法均在事务中执行，测试结束后自动回滚，不污染数据库。
 * - @ActiveProfiles("test")：加载 application-test.yml，连接真实 MySQL（gopair_test）。
 * - AdminContextHolder.clear()：每个测试方法后主动清理 ThreadLocal，防止测试间状态泄漏。
 * - AdminServiceTestConfig：提供真实 BCryptPasswordEncoder、
 *   同步 TaskExecutor（审计日志同步写入），以及真实 Mapper。
 * - 本服务不使用 Redis，无需 Redis 清理。
 *
 * @author gopair
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Transactional
public abstract class BaseIntegrationTest {

    @AfterEach
    void clearAdminContext() {
        AdminContextHolder.clear();
    }
}
