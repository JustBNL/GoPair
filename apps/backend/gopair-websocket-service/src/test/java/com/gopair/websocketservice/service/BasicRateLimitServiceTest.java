package com.gopair.websocketservice.service;

import com.gopair.websocketservice.config.TestConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * BasicRateLimitService 单元测试（新增）。
 *
 * * [核心策略]
 * - 作为门面，委托 TokenBucketRateLimitService 执行令牌桶限流。
 * - 对外保持原有方法签名不变，降低调用方改动成本。
 *
 * * [覆盖场景]
 * - Happy Path：委托 TokenBucketRateLimitService 并返回结果。
 * - Negative Path：resetUserMessageRate 委托 resetBucket。
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestConfig.class)
public class BasicRateLimitServiceTest {

    @MockBean
    private TokenBucketRateLimitService tokenBucketRateLimitService;

    @Autowired
    private BasicRateLimitService basicRateLimitService;

    private static final Long TEST_USER_ID = 9999L;

    // ==================== Happy Path ====================

    @Nested
    @DisplayName("Happy Path：限流检查")
    class RateLimitTests {

        @Test
        @DisplayName("checkMessageRateLimit(userId) 委托 TokenBucketRateLimitService 返回 true")
        void testCheckMessageRateLimitUserIdReturnsTrue() {
            when(tokenBucketRateLimitService.checkMessageRateLimit(TEST_USER_ID))
                    .thenReturn(true);

            boolean result = basicRateLimitService.checkMessageRateLimit(TEST_USER_ID);

            assertTrue(result);
            verify(tokenBucketRateLimitService, times(1))
                    .checkMessageRateLimit(TEST_USER_ID);
        }

        @Test
        @DisplayName("checkMessageRateLimit(userId, messageType) 委托 TokenBucketRateLimitService 返回 false")
        void testCheckMessageRateLimitWithMessageTypeReturnsFalse() {
            when(tokenBucketRateLimitService.checkMessageRateLimit(TEST_USER_ID, 1))
                    .thenReturn(false);

            boolean result = basicRateLimitService.checkMessageRateLimit(TEST_USER_ID, 1);

            assertFalse(result);
            verify(tokenBucketRateLimitService, times(1))
                    .checkMessageRateLimit(TEST_USER_ID, 1);
        }

        @Test
        @DisplayName("连续调用时 TokenBucketRateLimitService 被重复调用")
        void testMultipleCallsDelegated() {
            when(tokenBucketRateLimitService.checkMessageRateLimit(TEST_USER_ID))
                    .thenReturn(true);

            basicRateLimitService.checkMessageRateLimit(TEST_USER_ID);
            basicRateLimitService.checkMessageRateLimit(TEST_USER_ID, 2);

            verify(tokenBucketRateLimitService, times(1))
                    .checkMessageRateLimit(TEST_USER_ID);
            verify(tokenBucketRateLimitService, times(1))
                    .checkMessageRateLimit(TEST_USER_ID, 2);
        }

        @Test
        @DisplayName("不同消息类型委托时参数正确传递")
        void testDifferentMessageTypesDelegatedCorrectly() {
            when(tokenBucketRateLimitService.checkMessageRateLimit(anyLong(), anyInt()))
                    .thenReturn(true);

            basicRateLimitService.checkMessageRateLimit(1L, 1); // TEXT
            basicRateLimitService.checkMessageRateLimit(2L, 2); // IMAGE
            basicRateLimitService.checkMessageRateLimit(3L, 3); // FILE

            verify(tokenBucketRateLimitService, times(1)).checkMessageRateLimit(1L, 1);
            verify(tokenBucketRateLimitService, times(1)).checkMessageRateLimit(2L, 2);
            verify(tokenBucketRateLimitService, times(1)).checkMessageRateLimit(3L, 3);
        }
    }

    // ==================== Negative Path ====================

    @Nested
    @DisplayName("Negative Path：重置令牌桶")
    class ResetBucketTests {

        @Test
        @DisplayName("resetUserMessageRate 委托 resetBucket")
        void testResetUserMessageRateDelegatesToResetBucket() {
            doNothing().when(tokenBucketRateLimitService).resetBucket(TEST_USER_ID);

            basicRateLimitService.resetUserMessageRate(TEST_USER_ID);

            verify(tokenBucketRateLimitService, times(1)).resetBucket(TEST_USER_ID);
        }

        @Test
        @DisplayName("resetUserMessageRate 异常不传播")
        void testResetUserMessageRateSwallowsException() {
            doThrow(new RuntimeException("Redis error"))
                    .when(tokenBucketRateLimitService).resetBucket(anyLong());

            assertDoesNotThrow(() -> basicRateLimitService.resetUserMessageRate(1L));
        }
    }
}
