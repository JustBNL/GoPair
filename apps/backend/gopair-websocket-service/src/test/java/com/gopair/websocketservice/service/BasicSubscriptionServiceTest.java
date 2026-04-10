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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * BasicSubscriptionService 单元测试（重写版）。
 *
 * * [核心策略]
 * - SubscriptionManagerService 使用 @MockBean，完全通过 verify() 验证调用链。
 * - BasicSubscriptionService 使用 @Autowired，测试时无需关心 mock 还是真实 Bean。
 * - subscribeSilently 吞掉返回值，通过 verify(mock).subscribeChannel() 验证调用。
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestConfig.class)
public class BasicSubscriptionServiceTest {

    @MockBean
    private SubscriptionManagerService subscriptionManagerServiceMock;

    @Autowired
    private BasicSubscriptionService basicSubscriptionService;

    private String testSessionId;
    private Long testUserId;

    @BeforeEach
    void setUp() {
        testSessionId = "basic-sub-test-session-" + System.currentTimeMillis();
        testUserId = System.currentTimeMillis();
    }

    // ==================== Happy Path ====================

    @Nested
    @DisplayName("Happy Path：登录基础订阅")
    class LoginBasicSubscriptionTests {

        @Test
        @DisplayName("登录后订阅用户个人频道和系统全局频道，subscribeChannel 被调用")
        void testPerformLoginBasicSubscriptionSubscribesUserAndSystemChannels() {
            // given: subscribeChannel 返回 true
            when(subscriptionManagerServiceMock.subscribeChannel(
                    anyString(), anyLong(), anyString(), any(), anyString()))
                    .thenReturn(true);

            // when
            basicSubscriptionService.performLoginBasicSubscription(testSessionId, testUserId);

            // then: subscribeChannel 被调用两次（用户频道 + 系统频道）
            verify(subscriptionManagerServiceMock, times(2))
                    .subscribeChannel(eq(testSessionId), eq(testUserId), anyString(), any(), eq("auto"));
        }

        @Test
        @DisplayName("userId 为 null 时跳过基础订阅，不抛异常")
        void testLoginBasicSubscriptionWithNullUserId() {
            assertDoesNotThrow(() ->
                    basicSubscriptionService.performLoginBasicSubscription(testSessionId, null));
        }
    }

    // ==================== Happy Path：房间订阅 ====================

    @Nested
    @DisplayName("Happy Path：房间订阅")
    class RoomSubscriptionTests {

        @Test
        @DisplayName("subscribeToRoom 返回 true 当 subscribeChannel 返回 true")
        void testSubscribeToRoomSuccess() {
            // given: subscribeChannel 返回 true
            when(subscriptionManagerServiceMock.subscribeChannel(
                    anyString(), anyLong(), anyString(), any(), eq("manual")))
                    .thenReturn(true);

            // when
            boolean result = basicSubscriptionService.subscribeToRoom(testSessionId, testUserId, 100L);

            // then
            assertTrue(result, "subscribeChannel 返回 true 时 subscribeToRoom 应返回 true");
            verify(subscriptionManagerServiceMock).subscribeChannel(
                    eq(testSessionId), eq(testUserId), contains("room:chat:100"), any(), eq("manual"));
        }

        @Test
        @DisplayName("room 频道格式为 room:chat:{roomId}")
        void testRoomSubscriptionChannelFormat() {
            when(subscriptionManagerServiceMock.subscribeChannel(
                    anyString(), anyLong(), anyString(), any(), eq("manual")))
                    .thenReturn(true);

            basicSubscriptionService.subscribeToRoom(testSessionId, testUserId, 999L);

            verify(subscriptionManagerServiceMock).subscribeChannel(
                    eq(testSessionId), eq(testUserId), eq("room:chat:999"), any(), eq("manual"));
        }
    }

    // ==================== Negative Path ====================

    @Nested
    @DisplayName("Negative Path：参数校验与异常处理")
    class NegativePathTests {

        @Test
        @DisplayName("房间订阅时 sessionId 为 null 返回 false")
        void testSubscribeToRoomWithNullSessionId() {
            boolean result = basicSubscriptionService.subscribeToRoom(null, testUserId, 100L);
            assertFalse(result, "sessionId 为 null 应返回 false");
        }

        @Test
        @DisplayName("房间订阅时 userId 为 null 返回 false")
        void testSubscribeToRoomWithNullUserId() {
            boolean result = basicSubscriptionService.subscribeToRoom(testSessionId, null, 100L);
            assertFalse(result, "userId 为 null 应返回 false");
        }

        @Test
        @DisplayName("房间订阅时 roomId 为 null 返回 false")
        void testSubscribeToRoomWithNullRoomId() {
            boolean result = basicSubscriptionService.subscribeToRoom(testSessionId, testUserId, null);
            assertFalse(result, "roomId 为 null 应返回 false");
        }

        @Test
        @DisplayName("subscribeChannel 返回 false 时 subscribeToRoom 正确返回 false")
        void testSubscribeToRoomReturnsFalseWhenSubscriptionManagerFails() {
            // given: subscribeChannel 返回 false
            when(subscriptionManagerServiceMock.subscribeChannel(
                    anyString(), anyLong(), anyString(), any(), eq("manual")))
                    .thenReturn(false);

            // when
            boolean result = basicSubscriptionService.subscribeToRoom(testSessionId, testUserId, 777L);

            // then
            assertFalse(result, "subscribeChannel 返回 false 时 subscribeToRoom 应返回 false");
        }
    }
}
