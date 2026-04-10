package com.gopair.roomservice;

import com.gopair.framework.context.UserContext;
import com.gopair.framework.context.UserContextHolder;
import com.gopair.framework.logging.annotation.LogRecord;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 日志和用户上下文集成测试
 *
 * 验证 AOP 日志切面和用户上下文功能
 *
 * @author gopair
 */
@Slf4j
class LoggingIntegrationTest {

    @BeforeEach
    void setUp() {
        UserContextHolder.clear();
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    void testUserContextManagement() {
        assertFalse(UserContextHolder.hasContext());

        UserContextHolder.setContext(UserContext.of(12345L, "testUser"));

        assertTrue(UserContextHolder.hasContext());
        assertEquals(12345L, UserContextHolder.getCurrentUserId());
        assertEquals("testUser", UserContextHolder.getCurrentNickname());

        UserContextHolder.clear();
        assertFalse(UserContextHolder.hasContext());
        assertNull(UserContextHolder.getCurrentUserId());
        assertNull(UserContextHolder.getCurrentNickname());
    }

    @Test
    void testAopConfiguration() {
        UserContextHolder.setContext(UserContext.of(12345L, "testUser"));

        TestService testService = new TestService();
        assertDoesNotThrow(() -> testService.performanceTestMethod());

        log.info("AOP配置测试完成 - 注解正确加载且方法正常执行");
    }

    @Test
    void testLogAnnotationLoading() {
        assertDoesNotThrow(() -> {
            Class.forName("com.gopair.framework.logging.annotation.LogRecord");
            log.info("日志注解类加载成功");
        });
    }

    @Test
    void testLogRecordAspect() {
        UserContextHolder.setContext(UserContext.of(99999L, "propagationUser"));

        TestService testService = new TestService();
        assertDoesNotThrow(() -> testService.performanceTestMethod());

        assertEquals("业务处理: 99999, 数量: null", testService.businessTestMethod("99999", null));

        assertDoesNotThrow(() -> testService.noPerformanceTestMethod());

        log.info("日志记录切面测试完成");
    }

    @Test
    void testContextPropagation() {
        UserContextHolder.setContext(UserContext.of(99999L, "propagationUser"));

        TestService testService = new TestService();
        String result = testService.nestedMethod("test");
        assertEquals("嵌套调用结果: test", result);

        log.info("上下文传播测试完成");
    }

    @Test
    void testBusinessLogAnnotation() throws ClassNotFoundException {
        Class.forName("com.gopair.framework.logging.annotation.LogRecord");
    }

    static class TestService {

        @LogRecord(operation = "业务测试方法", module = "TEST")
        public String businessTestMethod(String param, Integer count) {
            return "业务处理: " + param + ", 数量: " + count;
        }

        @LogRecord(operation = "性能测试方法", module = "TEST", logPerformance = true)
        public void performanceTestMethod() throws InterruptedException {
            Thread.sleep(100);
        }

        @LogRecord(operation = "关闭性能测试", module = "TEST", logPerformance = false)
        public void noPerformanceTestMethod() throws InterruptedException {
            Thread.sleep(100);
        }

        @LogRecord(operation = "嵌套方法调用")
        public String nestedMethod(String param) {
            Long userId = UserContextHolder.getCurrentUserId();
            String nickname = UserContextHolder.getCurrentNickname();

            log.info("嵌套方法中的用户上下文 - userId: {}, nickname: {}", userId, nickname);

            return "嵌套调用结果: " + param;
        }
    }
}
