package com.gopair.websocketservice.service;

import com.gopair.websocketservice.config.TestConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * ScheduledTaskService 单元测试。
 *
 * * [核心策略]
 * - heartbeatTimeoutDetectionTask：扫描 sessions，对超过 90s 未活跃的会话强制关闭。
 * - ConnectionManagerService 使用 @SpyBean，通过 stub getSession() 提供 mock session。
 * - RedisOperationService 使用 @SpyBean，通过 stub getSession() 提供 Redis 数据。
 * - SubscriptionManagerService 使用 @MockBean。
 *
 * * [覆盖场景]
 * - Happy Path：心跳超时检测、会话清理、各定时任务调用验证。
 * - Negative Path：Redis 操作失败、session.close 异常均被捕获不传播。
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestConfig.class)
public class ScheduledTaskServiceTest {

    @SpyBean
    private RedisOperationService redisOperationService;

    @MockBean
    private SubscriptionManagerService subscriptionManagerService;

    @SpyBean
    private ConnectionManagerService connectionManager;

    @Autowired
    private ScheduledTaskService scheduledTaskService;

    @BeforeEach
    void setUp() {
        reset(subscriptionManagerService);

        doNothing().when(subscriptionManagerService).performExpiredPermissionsCleanup();
        doNothing().when(subscriptionManagerService).performDailySubscriptionCleanup();

        lenient().when(connectionManager.getAllSessionIds()).thenReturn(Collections.emptySet());
    }

    // ==================== Happy Path：心跳超时检测 ====================

    @Nested
    @DisplayName("Happy Path：心跳超时检测")
    class HeartbeatTimeoutTests {

        @Test
        @DisplayName("检测到心跳超时会话，任务执行成功")
        void testHeartbeatTimeoutClosesSession() throws Exception {
            String timedOutSessionId = "timed-out-" + System.currentTimeMillis();
            WebSocketSession timedOutSession = mock(WebSocketSession.class);
            when(timedOutSession.getId()).thenReturn(timedOutSessionId);
            when(timedOutSession.isOpen()).thenReturn(true);

            Map<Object, Object> redisData = new HashMap<>();
            redisData.put("lastActiveTime", LocalDateTime.now().minusSeconds(91).toString());

            lenient().when(connectionManager.getAllSessionIds()).thenReturn(Set.of(timedOutSessionId));
            lenient().when(connectionManager.getSession(timedOutSessionId)).thenReturn(timedOutSession);
            lenient().when(redisOperationService.getSession(timedOutSessionId)).thenReturn(redisData);

            scheduledTaskService.heartbeatTimeoutDetectionTask();
        }

        @Test
        @DisplayName("未超时会话不关闭")
        void testActiveSessionNotClosed() throws Exception {
            String activeSessionId = "active-" + System.currentTimeMillis();
            WebSocketSession activeSession = mock(WebSocketSession.class);
            when(activeSession.getId()).thenReturn(activeSessionId);
            when(activeSession.isOpen()).thenReturn(true);

            Map<Object, Object> redisData = new HashMap<>();
            redisData.put("lastActiveTime", LocalDateTime.now().minusSeconds(10).toString());

            lenient().when(connectionManager.getAllSessionIds()).thenReturn(Set.of(activeSessionId));
            lenient().when(connectionManager.getSession(activeSessionId)).thenReturn(activeSession);
            lenient().when(redisOperationService.getSession(activeSessionId)).thenReturn(redisData);

            scheduledTaskService.heartbeatTimeoutDetectionTask();

            verify(activeSession, never()).close(any(CloseStatus.class));
        }

        @Test
        @DisplayName("无活跃会话时不抛异常")
        void testNoSessionsDoesNotThrow() {
            assertDoesNotThrow(() -> scheduledTaskService.heartbeatTimeoutDetectionTask());
        }

        @Test
        @DisplayName("心跳超时会话已关闭时不重复关闭")
        void testAlreadyClosedSessionNotClosedAgain() throws Exception {
            String closedSessionId = "already-closed-" + System.currentTimeMillis();
            WebSocketSession closedSession = mock(WebSocketSession.class);
            when(closedSession.getId()).thenReturn(closedSessionId);
            when(closedSession.isOpen()).thenReturn(false);

            Map<Object, Object> redisData = new HashMap<>();
            redisData.put("lastActiveTime", LocalDateTime.now().minusSeconds(91).toString());

            lenient().when(connectionManager.getAllSessionIds()).thenReturn(Set.of(closedSessionId));
            lenient().when(connectionManager.getSession(closedSessionId)).thenReturn(closedSession);
            lenient().when(redisOperationService.getSession(closedSessionId)).thenReturn(redisData);

            scheduledTaskService.heartbeatTimeoutDetectionTask();

            verify(closedSession, never()).close(any(CloseStatus.class));
        }

        @Test
        @DisplayName("lastActiveTime 为 null 时跳过该会话")
        void testNullLastActiveTimeSkipsSession() throws Exception {
            String sessionId = "null-last-active-" + System.currentTimeMillis();
            WebSocketSession mockSession = mock(WebSocketSession.class);
            when(mockSession.getId()).thenReturn(sessionId);
            when(mockSession.isOpen()).thenReturn(true);

            lenient().when(connectionManager.getAllSessionIds()).thenReturn(Set.of(sessionId));
            lenient().when(connectionManager.getSession(sessionId)).thenReturn(mockSession);
            lenient().when(redisOperationService.getSession(sessionId)).thenReturn(Collections.emptyMap());

            assertDoesNotThrow(() -> scheduledTaskService.heartbeatTimeoutDetectionTask());
            verify(mockSession, never()).close(any(CloseStatus.class));
        }
    }

    // ==================== Happy Path：定时任务 ====================

    @Nested
    @DisplayName("Happy Path：定时任务")
    class ScheduledTaskTests {

        @Test
        @DisplayName("lightweightRedisCheckTask 调用 performLightweightRedisCheck")
        void testLightweightRedisCheckTask() {
            scheduledTaskService.lightweightRedisCheckTask();

            verify(redisOperationService, times(1)).performLightweightRedisCheck();
        }

        @Test
        @DisplayName("hourlyRedisCleanupTask 调用 performHourlyRedisCleanup")
        void testHourlyRedisCleanupTask() {
            scheduledTaskService.hourlyRedisCleanupTask();

            verify(redisOperationService, times(1)).performHourlyRedisCleanup();
        }

        @Test
        @DisplayName("dailyDeepRedisCleanupTask 调用 performDailyDeepRedisCleanup")
        void testDailyDeepRedisCleanupTask() {
            scheduledTaskService.dailyDeepRedisCleanupTask();

            verify(redisOperationService, times(1)).performDailyDeepRedisCleanup();
        }

        @Test
        @DisplayName("cleanupExpiredPermissions 调用 performExpiredPermissionsCleanup")
        void testCleanupExpiredPermissionsTask() {
            scheduledTaskService.cleanupExpiredPermissions();

            verify(subscriptionManagerService, times(1)).performExpiredPermissionsCleanup();
        }

        @Test
        @DisplayName("dailySubscriptionCleanupTask 调用 performDailySubscriptionCleanup")
        void testDailySubscriptionCleanupTask() {
            scheduledTaskService.dailySubscriptionCleanupTask();

            verify(subscriptionManagerService, times(1)).performDailySubscriptionCleanup();
        }
    }

    // ==================== Negative Path ====================

    @Nested
    @DisplayName("Negative Path：异常处理")
    class NegativePathTests {

        @Test
        @DisplayName("lightweightRedisCheckTask Redis 异常不传播")
        void testLightweightRedisCheckTaskSwallowsException() {
            scheduledTaskService.lightweightRedisCheckTask();
            verify(redisOperationService, times(1)).performLightweightRedisCheck();
        }

        @Test
        @DisplayName("hourlyRedisCleanupTask Redis 异常不传播")
        void testHourlyRedisCleanupTaskSwallowsException() {
            scheduledTaskService.hourlyRedisCleanupTask();
            verify(redisOperationService, times(1)).performHourlyRedisCleanup();
        }

        @Test
        @DisplayName("heartbeatTimeoutDetectionTask 中 getAllSessionIds 异常不传播")
        void testHeartbeatTimeoutHandlesGetAllSessionIdsException() {
            doThrow(new RuntimeException("Redis connection error"))
                    .when(connectionManager).getAllSessionIds();

            assertDoesNotThrow(() -> scheduledTaskService.heartbeatTimeoutDetectionTask());
        }
    }
}
