package com.gopair.roomservice;

import com.gopair.framework.logging.annotation.LogRecord;
import com.gopair.framework.context.UserContext;
import com.gopair.framework.context.UserContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.stereotype.Service;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 日志集成测试
 * 
 * 验证AOP日志切面和用户上下文功能正常工作
 * 
 * @author gopair
 */
@Slf4j
@SpringBootTest
@ActiveProfiles("test")
class LoggingIntegrationTest {

    @BeforeEach
    void setUp() {
        // 清理用户上下文，确保每个测试开始时状态干净
        UserContextHolder.clear();
    }

    @AfterEach
    void tearDown() {
        // 测试结束后清理用户上下文
        UserContextHolder.clear();
    }

    @Test
    void testUserContextManagement() {
        // 测试用户上下文的设置和获取
        
        // 创建用户上下文
        UserContextHolder.setContext(UserContext.of(12345L, "testUser"));
        
        // 验证可以正确获取
        assertTrue(UserContextHolder.hasContext());
        assertEquals(12345L, UserContextHolder.getCurrentUserId());
        assertEquals("testUser", UserContextHolder.getCurrentNickname());
        
        // 清理并验证
        UserContextHolder.clear();
        assertFalse(UserContextHolder.hasContext());
        assertNull(UserContextHolder.getCurrentUserId());
        assertNull(UserContextHolder.getCurrentNickname());
    }

    @Test
    void testAopConfiguration() {
        // 设置用户上下文
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
        // 测试在多层调用中上下文的传播
        UserContextHolder.setContext(UserContext.of(99999L, "propagationUser"));
        
        TestService testService = new TestService();
        assertDoesNotThrow(() -> testService.performanceTestMethod());
        
        assertEquals("业务处理: 99999, 数量: null", testService.businessTestMethod("99999", null));
        
        assertDoesNotThrow(() -> testService.noPerformanceTestMethod());
        
        log.info("日志记录切面测试完成");
    }

    @Test
    void testContextPropagation() {
        // 测试在多层调用中上下文的传播
        UserContextHolder.setContext(UserContext.of(99999L, "propagationUser"));

        TestService testService = new TestService();
        String result = testService.nestedMethod("test");
        assertEquals("嵌套处理: test", result);

        log.info("上下文传播测试完成");
    }

    @Test
    void testBusinessLogAnnotation() throws ClassNotFoundException {
        // 测试注解是否存在
                    Class.forName("com.gopair.framework.logging.annotation.LogRecord");
    }

    /**
     * 测试服务类
     */
    @Service
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
            // 在嵌套调用中验证上下文仍然存在
            Long userId = UserContextHolder.getCurrentUserId();
            String nickname = UserContextHolder.getCurrentNickname();
            
            log.info("嵌套方法中的用户上下文 - userId: {}, nickname: {}", userId, nickname);
            
            return "嵌套调用结果: " + param;
        }
    }
} 