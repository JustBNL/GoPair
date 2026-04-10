package com.gopair.websocketservice.service;

import com.gopair.websocketservice.config.TestConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.WebSocketSession;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ConnectionManagerService 单元测试。
 *
 * * [核心策略]
 * - RedisOperationService 使用 @MockBean，完全控制 Redis 操作行为。
 * - ConnectionManagerService 通过 @Autowired 使用真实 bean 实例。
 * - 注意：ConnectionManagerService 依赖的是 SessionStore 接口，实际注入的是 RedisOperationService。
 * - 每个测试在执行前设置 Redis 操作的 stub，确保行为可预测。
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestConfig.class)
public class ConnectionManagerServiceTest {

    @MockBean
    private RedisOperationService redisOperationService;

    @Autowired
    private ConnectionManagerService connectionManager;

    private WebSocketSession mockSession;
    private String testSessionId;
    private Long testUserId;

    @BeforeEach
    void setUp() {
        testSessionId = "conn-mgr-test-session-" + System.currentTimeMillis();
        testUserId = System.currentTimeMillis();

        mockSession = mock(WebSocketSession.class);
        when(mockSession.getId()).thenReturn(testSessionId);
        when(mockSession.isOpen()).thenReturn(true);

        // 重置所有 mock 行为
        reset(redisOperationService);

        // Redis 操作默认 no-op
        doNothing().when(redisOperationService).saveSession(anyString(), anyLong(), anyString(), any(), anyString(), anyLong());
        doNothing().when(redisOperationService).addUserSession(anyLong(), anyString());
        doNothing().when(redisOperationService).removeSession(anyString());
        doNothing().when(redisOperationService).removeUserSession(anyLong(), anyString());
        doNothing().when(redisOperationService).refreshSessionTtl(anyString(), anyLong());
        when(redisOperationService.getSession(anyString())).thenReturn(java.util.Collections.emptyMap());
        when(redisOperationService.getUserSessions(anyLong())).thenReturn(java.util.Collections.emptySet());
    }

    // ==================== Happy Path ====================

    @Nested
    @DisplayName("Happy Path：会话添加与移除")
    class SessionManagementTests {

        @Test
        @DisplayName("addGlobalSession 调用 RedisOperationService 保存并添加 userSessions 索引")
        void testAddGlobalSessionWritesToMapAndRedis() {
            // when
            connectionManager.addGlobalSession(mockSession, testUserId);

            // then: RedisOperationService 保存被调用
            verify(redisOperationService, times(1)).saveSession(
                    eq(testSessionId), eq(testUserId), eq("global"), isNull(), anyString(), anyLong());
            verify(redisOperationService, times(1)).addUserSession(eq(testUserId), eq(testSessionId));
        }

        @Test
        @DisplayName("removeSessionAndGetUserId 读取 Redis 数据并清理索引")
        void testRemoveSessionAndGetUserIdReturnsUserId() {
            // given: getSession 返回 userId 数据
            when(redisOperationService.getSession(testSessionId))
                    .thenReturn(java.util.Map.of("userId", testUserId.toString()));

            // when
            Long result = connectionManager.removeSessionAndGetUserId(testSessionId);

            // then
            assertEquals(testUserId, result, "应返回正确的 userId");
            verify(redisOperationService, times(1)).removeSession(testSessionId);
            verify(redisOperationService, times(1)).removeUserSession(eq(testUserId), eq(testSessionId));
        }

        @Test
        @DisplayName("getAllSessionIds 返回所有活跃 session ID")
        void testGetAllSessionIds() {
            // given: 添加两个会话
            WebSocketSession session2 = mock(WebSocketSession.class);
            String sessionId2 = "session-2-" + System.currentTimeMillis();
            when(session2.getId()).thenReturn(sessionId2);
            when(session2.isOpen()).thenReturn(true);

            connectionManager.addGlobalSession(mockSession, testUserId);
            connectionManager.addGlobalSession(session2, testUserId + 1);

            // when
            Set<String> allIds = connectionManager.getAllSessionIds();

            // then: 至少包含这两个 session
            assertTrue(allIds.contains(testSessionId), "应包含 testSessionId");
            assertTrue(allIds.contains(sessionId2), "应包含 sessionId2");
        }

        @Test
        @DisplayName("updateHeartbeat 刷新 Redis TTL")
        void testUpdateHeartbeatRefreshesTtl() {
            // when
            connectionManager.updateHeartbeat(testSessionId);

            // then
            verify(redisOperationService, times(1))
                    .refreshSessionTtl(eq(testSessionId), anyLong());
        }

        @Test
        @DisplayName("getActiveSessionCount 返回活跃会话数")
        void testGetActiveSessionCount() {
            // given
            int before = connectionManager.getActiveSessionCount();

            // when
            connectionManager.addGlobalSession(mockSession, testUserId);

            // then
            assertEquals(before + 1, connectionManager.getActiveSessionCount(),
                    "活跃会话数应增加");
        }

        @Test
        @DisplayName("getConnectionStats 返回包含 totalSessions 的 Map")
        void testGetConnectionStats() {
            var stats = connectionManager.getConnectionStats();

            assertNotNull(stats);
            assertTrue(stats.containsKey("totalSessions"));
        }
    }

    // ==================== Edge Cases ====================

    @Nested
    @DisplayName("Edge Cases：session 不存在与 userId 解析")
    class EdgeCaseTests {

        @Test
        @DisplayName("getSession 对不存在的 sessionId 返回 null")
        void testGetSessionReturnsNullForNonExistent() {
            // getSession 读取的是内存 Map，不存在的 key 返回 null
            var result = connectionManager.getSession("non-existent-session-id");
            assertNull(result, "不存在的 session 应返回 null");
        }

        @Test
        @DisplayName("removeSessionAndGetUserId 对不存在的 sessionId 返回 null")
        void testRemoveSessionAndGetUserIdReturnsNullForNonExistent() {
            when(redisOperationService.getSession("non-existent"))
                    .thenReturn(java.util.Collections.emptyMap());

            Long result = connectionManager.removeSessionAndGetUserId("non-existent");

            assertNull(result, "不存在的 sessionId 应返回 null");
        }

        @Test
        @DisplayName("removeSessionAndGetUserId 解析 userId 失败时返回 null，不抛异常")
        void testRemoveSessionAndGetUserIdHandlesParseFailure() {
            // given: getSession 返回非数字 userId
            when(redisOperationService.getSession(testSessionId))
                    .thenReturn(java.util.Map.of("userId", "not-a-number"));

            // when
            Long result = connectionManager.removeSessionAndGetUserId(testSessionId);

            // then: 返回 null（因为 NumberFormatException 被捕获）
            assertNull(result, "userId 解析失败时应返回 null");
        }

        @Test
        @DisplayName("getSessionInfo 返回 null 当 Redis 无数据")
        void testGetSessionInfoReturnsNullWhenNoData() {
            when(redisOperationService.getSession(testSessionId))
                    .thenReturn(java.util.Collections.emptyMap());

            var info = connectionManager.getSessionInfo(testSessionId);

            assertNull(info, "无数据时应返回 null");
        }

        @Test
        @DisplayName("getSessionInfo 正常解析 Redis 中的会话信息")
        void testGetSessionInfoParsesCorrectly() {
            // given
            when(redisOperationService.getSession(testSessionId))
                    .thenReturn(java.util.Map.of(
                            "userId", testUserId.toString(),
                            "connectionType", "global",
                            "roomId", "123",
                            "connectTime", "2026-03-15T10:00:00",
                            "lastActiveTime", "2026-03-15T10:30:00"
                    ));

            // when
            var info = connectionManager.getSessionInfo(testSessionId);

            // then
            assertNotNull(info);
            assertEquals(testSessionId, info.getSessionId());
            assertEquals(testUserId, info.getUserId());
            assertEquals("global", info.getConnectionType());
            assertEquals(123L, info.getRoomId());
        }
    }

    // ==================== Negative Path ====================

    @Nested
    @DisplayName("Negative Path：异常处理")
    class NegativePathTests {

        @Test
        @DisplayName("getSessionInfo 读取 Redis 异常时返回 null")
        void testGetSessionInfoReturnsNullOnException() {
            when(redisOperationService.getSession(testSessionId))
                    .thenThrow(new RuntimeException("Redis error"));

            var info = connectionManager.getSessionInfo(testSessionId);

            assertNull(info, "Redis 异常时应返回 null");
        }

        @Test
        @DisplayName("getSessionLastActiveTime 解析失败时返回 null")
        void testGetSessionLastActiveTimeReturnsNullOnParseFailure() {
            when(redisOperationService.getSession(testSessionId))
                    .thenReturn(java.util.Map.of("lastActiveTime", "not-a-date"));

            var result = connectionManager.getSessionLastActiveTime(testSessionId);

            assertNull(result, "解析失败时应返回 null");
        }

        @Test
        @DisplayName("updateHeartbeat 在 Redis 异常时直接传播")
        void testUpdateHeartbeatPropagatesException() {
            doThrow(new RuntimeException("Redis error"))
                    .when(redisOperationService).refreshSessionTtl(anyString(), anyLong());

            assertThrows(RuntimeException.class, () -> connectionManager.updateHeartbeat(testSessionId),
                    "Redis 异常时应传播到外层");
        }
    }
}
