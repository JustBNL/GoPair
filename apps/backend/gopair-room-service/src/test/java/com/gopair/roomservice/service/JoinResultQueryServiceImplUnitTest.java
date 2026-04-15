package com.gopair.roomservice.service;

import com.gopair.roomservice.service.JoinResultQueryService.JoinStatusVO;
import com.gopair.roomservice.service.impl.JoinResultQueryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * JoinResultQueryService 单元测试。
 *
 * * [核心策略]
 * - 隔离性：使用 @ExtendWith(MockitoExtension.class)，Redis 完全 @Mock。
 * - 目标明确：覆盖 queryByToken 的所有分支（JOINED / FAILED / PROCESSING / token 不存在）。
 *
 * * [测试范围]
 * - token 存在：JOINED → 返回 JOINED 状态
 * - token 存在：FAILED → 返回 FAILED 状态
 * - token 不存在 → 返回 PROCESSING 状态
 * - 裸 PROCESSING 值 → 兼容处理
 */
@ExtendWith(MockitoExtension.class)
class JoinResultQueryServiceImplUnitTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private JoinResultQueryServiceImpl joinResultQueryService;

    @BeforeEach
    void setUp() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        joinResultQueryService = new JoinResultQueryServiceImpl(stringRedisTemplate);
    }

    @Nested
    @DisplayName("queryByToken 分支覆盖")
    class QueryByTokenTests {

        @Test
        @DisplayName("token 不存在 → PROCESSING")
        void tokenNotExists_ShouldReturnProcessing() {
            when(valueOperations.get("join:abc123token")).thenReturn(null);

            JoinStatusVO result =
                    joinResultQueryService.queryByToken("abc123token");

            assertEquals(JoinStatusVO.Status.PROCESSING, result.status);
            assertNull(result.roomId);
            assertNull(result.userId);
            assertEquals("处理中", result.message);
        }

        @Test
        @DisplayName("token = JOINED 格式 → 返回 JOINED 状态")
        void tokenJoined_ShouldReturnJoinedStatus() {
            when(valueOperations.get("join:abc123token")).thenReturn("123:456:JOINED");

            JoinStatusVO result =
                    joinResultQueryService.queryByToken("abc123token");

            assertEquals(JoinStatusVO.Status.JOINED, result.status);
            assertEquals(123L, result.roomId);
            assertEquals(456L, result.userId);
            assertEquals("加入成功", result.message);
        }

        @Test
        @DisplayName("token = FAILED 格式 → 返回 FAILED 状态")
        void tokenFailed_ShouldReturnFailedStatus() {
            when(valueOperations.get("join:abc123token")).thenReturn("789:101:FAILED");

            JoinStatusVO result =
                    joinResultQueryService.queryByToken("abc123token");

            assertEquals(JoinStatusVO.Status.FAILED, result.status);
            assertEquals(789L, result.roomId);
            assertEquals(101L, result.userId);
            assertEquals("加入失败", result.message);
        }

        @Test
        @DisplayName("裸 PROCESSING 值 → 兼容返回 PROCESSING")
        void tokenBareProcessing_ShouldReturnProcessing() {
            when(valueOperations.get("join:abc123token")).thenReturn("PROCESSING");

            JoinStatusVO result =
                    joinResultQueryService.queryByToken("abc123token");

            assertEquals(JoinStatusVO.Status.PROCESSING, result.status);
        }

        @Test
        @DisplayName("未知状态值 → PROCESSING")
        void tokenUnknownStatus_ShouldReturnProcessing() {
            when(valueOperations.get("join:abc123token")).thenReturn("123:456:UNKNOWN");

            JoinStatusVO result =
                    joinResultQueryService.queryByToken("abc123token");

            assertEquals(JoinStatusVO.Status.PROCESSING, result.status);
        }

        @Test
        @DisplayName("格式非法（不足3段）→ PROCESSING")
        void tokenInvalidFormat_ShouldReturnProcessing() {
            when(valueOperations.get("join:abc123token")).thenReturn("just-a-string");

            JoinStatusVO result =
                    joinResultQueryService.queryByToken("abc123token");

            assertEquals(JoinStatusVO.Status.PROCESSING, result.status);
        }

        @Test
        @DisplayName("roomId/userId 格式非法 → 解析为 null，不崩溃")
        void tokenWithInvalidId_ShouldReturnNullIds() {
            when(valueOperations.get("join:abc123token")).thenReturn("abc:xyz:JOINED");

            JoinStatusVO result =
                    joinResultQueryService.queryByToken("abc123token");

            assertEquals(JoinStatusVO.Status.JOINED, result.status);
            assertNull(result.roomId);
            assertNull(result.userId);
        }
    }
}
