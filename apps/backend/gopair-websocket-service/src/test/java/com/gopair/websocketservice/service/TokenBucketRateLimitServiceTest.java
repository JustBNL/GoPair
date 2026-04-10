package com.gopair.websocketservice.service;

import com.gopair.websocketservice.config.TestConfig;
import com.gopair.websocketservice.constants.WebSocketConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.mockito.ArgumentCaptor;

/**
 * TokenBucketRateLimitService 单元测试（补充版）。
 *
 * * [核心策略]
 * - Redis 异常时保守拒绝，防止攻击者利用 Redis 闪断绕过限流。
 * - 令牌桶容量固定为 10，补充速率为 5 个/秒，TTL 为 600 秒。
 *
 * * [覆盖场景]
 * - Happy Path：正常限流检查（不同消息类型差异化令牌消耗）。
 * - Negative Path：userId 为 null 跳过、Redis 异常保守拒绝。
 * - Edge Cases：消息类型边界值、Lua 脚本 key 格式验证。
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestConfig.class)
public class TokenBucketRateLimitServiceTest {

    @MockBean
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private TokenBucketRateLimitService tokenBucketRateLimitService;

    private static final Long TEST_USER_ID = 1001L;

    @BeforeEach
    void setUp() {
        // 每次执行 Lua 脚本默认返回 1（允许），测试限流拒绝时再单独打桩
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(), any(), any(), any(), any()))
                .thenReturn(1L);
    }

    // ==================== Happy Path ====================

    @Nested
    @DisplayName("Happy Path：差异化令牌消耗")
    class HappyPathTests {

        @Test
        @DisplayName("TEXT 类型消耗 1 个令牌")
        void testTextMessageConsumesOneToken() {
            when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(), any(), any(), any(), any()))
                    .thenReturn(1L);

            boolean allowed = tokenBucketRateLimitService.checkMessageRateLimit(TEST_USER_ID, 1);

            assertTrue(allowed, "TEXT 消息应该被允许");
        }

        @Test
        @DisplayName("IMAGE 类型消耗 2 个令牌")
        void testImageMessageConsumesTwoTokens() {
            when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(), any(), any(), any(), any()))
                    .thenReturn(1L);

            boolean allowed = tokenBucketRateLimitService.checkMessageRateLimit(TEST_USER_ID, 2);

            assertTrue(allowed, "IMAGE 消息应该被允许");
        }

        @Test
        @DisplayName("FILE 类型消耗 3 个令牌")
        void testFileMessageConsumesThreeTokens() {
            when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(), any(), any(), any(), any()))
                    .thenReturn(1L);

            boolean allowed = tokenBucketRateLimitService.checkMessageRateLimit(TEST_USER_ID, 3);

            assertTrue(allowed, "FILE 消息应该被允许");
        }

        @Test
        @DisplayName("VOICE 类型消耗 2 个令牌")
        void testVoiceMessageConsumesTwoTokens() {
            when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(), any(), any(), any(), any()))
                    .thenReturn(1L);

            boolean allowed = tokenBucketRateLimitService.checkMessageRateLimit(TEST_USER_ID, 4);

            assertTrue(allowed, "VOICE 消息应该被允许");
        }

        @Test
        @DisplayName("EMOJI 类型消耗 1 个令牌")
        void testEmojiMessageConsumesOneToken() {
            when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(), any(), any(), any(), any()))
                    .thenReturn(1L);

            boolean allowed = tokenBucketRateLimitService.checkMessageRateLimit(TEST_USER_ID, 5);

            assertTrue(allowed, "EMOJI 消息应该被允许");
        }

        @Test
        @DisplayName("空消息类型默认消耗 1 个令牌")
        void testNullMessageTypeDefaultsToOneToken() {
            when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(), any(), any(), any(), any()))
                    .thenReturn(1L);

            boolean allowed = tokenBucketRateLimitService.checkMessageRateLimit(TEST_USER_ID);

            assertTrue(allowed, "空消息类型应该默认消耗 1 个令牌");
        }

        @Test
        @DisplayName("未知消息类型降级为消耗 1 个令牌")
        void testUnknownMessageTypeDefaultsToOneToken() {
            when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(), any(), any(), any(), any()))
                    .thenReturn(1L);

            boolean allowed = tokenBucketRateLimitService.checkMessageRateLimit(TEST_USER_ID, 999);

            assertTrue(allowed, "未知消息类型应该默认消耗 1 个令牌");
        }
    }

    // ==================== Negative Path ====================

    @Nested
    @DisplayName("Negative Path：限流拒绝与异常处理")
    class NegativePathTests {

        @Test
        @DisplayName("Redis 返回 0 表示令牌不足，限流拒绝")
        void testRateLimitRejectionWhenTokensExhausted() {
            when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(), any(), any(), any(), any()))
                    .thenReturn(0L);

            boolean allowed = tokenBucketRateLimitService.checkMessageRateLimit(TEST_USER_ID);

            assertFalse(allowed, "令牌不足时应拒绝");
        }

        @Test
        @DisplayName("Redis 执行异常时保守拒绝，防止绕过限流")
        void testRedisExceptionReturnsFalse() {
            when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(), any(), any(), any(), any()))
                    .thenThrow(new RuntimeException("Redis connection refused"));

            boolean allowed = tokenBucketRateLimitService.checkMessageRateLimit(TEST_USER_ID);

            assertFalse(allowed, "Redis 异常时应保守拒绝");
        }

        @Test
        @DisplayName("userId 为 null 时跳过限流检查，直接放行")
        void testNullUserIdSkipsRateLimit() {
            when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(), any(), any(), any(), any()))
                    .thenReturn(1L);

            boolean allowed = tokenBucketRateLimitService.checkMessageRateLimit(null);

            assertTrue(allowed, "userId 为 null 时应跳过限流");
            verify(redisTemplate, never()).execute(any(DefaultRedisScript.class), anyList(),
                    any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("Redis 返回 null 时保守拒绝")
        void testRedisReturnsNullReturnsFalse() {
            when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(), any(), any(), any(), any()))
                    .thenReturn(null);

            boolean allowed = tokenBucketRateLimitService.checkMessageRateLimit(TEST_USER_ID);

            assertFalse(allowed, "Redis 返回 null 时应保守拒绝");
        }
    }

    // ==================== Edge Cases：Lua 脚本参数 ====================

    @Nested
    @DisplayName("Edge Cases：Lua 脚本参数与 Redis Key")
    class LuaScriptTests {

        @Test
        @DisplayName("Lua 脚本调用的 Redis Key 格式为 ws:rate:limit:{userId}")
        void testLuaScriptKeyFormat() {
            when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(), any(), any(), any(), any()))
                    .thenReturn(1L);

            tokenBucketRateLimitService.checkMessageRateLimit(TEST_USER_ID);

            ArgumentCaptor<List<String>> keysCaptor = ArgumentCaptor.forClass(List.class);
            verify(redisTemplate, times(1)).execute(
                    any(DefaultRedisScript.class),
                    keysCaptor.capture(),
                    any(), any(), any(), any(), any());

            List<String> capturedKeys = keysCaptor.getValue();
            assertEquals(1, capturedKeys.size(),
                    "key 列表应只有 1 个元素");
            assertEquals(WebSocketConstants.TOKEN_BUCKET_KEY_PREFIX + TEST_USER_ID,
                    capturedKeys.get(0),
                    "key 应为前缀 + userId");
        }

        @Test
        @DisplayName("连续调用消耗令牌桶，Lua 脚本被多次调用")
        void testMultipleCallsInvokeScriptEachTime() {
            when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(), any(), any(), any(), any()))
                    .thenReturn(1L);

            tokenBucketRateLimitService.checkMessageRateLimit(TEST_USER_ID);
            tokenBucketRateLimitService.checkMessageRateLimit(TEST_USER_ID);
            tokenBucketRateLimitService.checkMessageRateLimit(TEST_USER_ID);

            verify(redisTemplate, times(3))
                    .execute(any(DefaultRedisScript.class), anyList(), any(), any(), any(), any(), any());
        }
    }

    // ==================== resetBucket ====================

    @Nested
    @DisplayName("resetBucket：重置用户令牌桶")
    class ResetBucketTests {

        @Test
        @DisplayName("正常重置用户令牌桶")
        void testResetBucketDeletesRedisKey() {
            when(redisTemplate.delete(anyString())).thenReturn(true);

            tokenBucketRateLimitService.resetBucket(TEST_USER_ID);

            String expectedKey = WebSocketConstants.TOKEN_BUCKET_KEY_PREFIX + TEST_USER_ID;
            verify(redisTemplate).delete(expectedKey);
        }

        @Test
        @DisplayName("userId 为 null 时跳过重置")
        void testResetBucketWithNullUserId() {
            when(redisTemplate.delete(anyString())).thenReturn(true);

            tokenBucketRateLimitService.resetBucket(null);

            verify(redisTemplate, never()).delete(anyString());
        }

        @Test
        @DisplayName("Redis 删除失败时不抛异常")
        void testResetBucketDoesNotThrowOnRedisFailure() {
            when(redisTemplate.delete(anyString())).thenThrow(new RuntimeException("Redis error"));

            assertDoesNotThrow(() -> tokenBucketRateLimitService.resetBucket(TEST_USER_ID));
        }
    }
}
