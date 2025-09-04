package com.gopair.websocketservice;

import com.gopair.websocketservice.config.TestConfig;

import com.gopair.websocketservice.domain.ChannelSubscription;
import com.gopair.websocketservice.service.*;
import com.gopair.common.core.R;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.WebSocketSession;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * WebSocket服务综合测试
 * 涵盖基础功能、订阅管理、监控API等核心功能
 * 
 * @author gopair
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestConfig.class)
public class WebSocketServiceTest {

    @MockBean
    private SubscriptionManagerService subscriptionManager;

    @MockBean
    private BasicSubscriptionService basicSubscriptionService;

    @MockBean
    private RedisOperationService redisOperationService;

    @MockBean
    private BasicRateLimitService basicRateLimit;

    @MockBean
    private ConnectionManagerService connectionManager;

    private WebSocketSession mockSession;
    private Long testUserId;
    private String testSessionId;

    @BeforeEach
    void setUp() {
        mockSession = mock(WebSocketSession.class);
        testUserId = System.currentTimeMillis();
        testSessionId = "test-session-" + testUserId;
        
        when(mockSession.getId()).thenReturn(testSessionId);
        when(mockSession.isOpen()).thenReturn(true);
        
        ConnectionManagerService.SessionInfo sessionInfo = new ConnectionManagerService.SessionInfo();
        sessionInfo.setSessionId(testSessionId);
        sessionInfo.setUserId(testUserId);
        sessionInfo.setConnectionType("global");
        sessionInfo.setConnectTime(LocalDateTime.now());
        sessionInfo.setLastHeartbeat(LocalDateTime.now());
        
        when(connectionManager.getSessionInfo(testSessionId)).thenReturn(sessionInfo);
    }

    /**
     * 测试基本订阅功能
     */
    @Test
    void testBasicSubscription() {
        String channel = "room:chat:123";
        Set<String> eventTypes = Set.of("message", "typing");
        
        boolean result = subscriptionManager.subscribeChannel(
            testSessionId, testUserId, channel, eventTypes, "manual");
        
        assertTrue(result, "订阅应该成功");
        
        Set<ChannelSubscription> userSubs = subscriptionManager.getUserSubscriptions(testUserId);
        assertEquals(1, userSubs.size(), "用户应该有1个订阅");
        
        ChannelSubscription subscription = userSubs.iterator().next();
        assertEquals(channel, subscription.getChannel());
        assertEquals(eventTypes, subscription.getEventTypes());
        assertEquals("manual", subscription.getSource());
    }

    /**
     * 测试取消订阅功能
     */
    @Test
    void testUnsubscription() {
        String channel = "room:file:456";
        Set<String> eventTypes = Set.of("upload", "download");
        
        subscriptionManager.subscribeChannel(
            testSessionId, testUserId, channel, eventTypes, "manual");
        
        Set<ChannelSubscription> userSubs = subscriptionManager.getUserSubscriptions(testUserId);
        assertEquals(1, userSubs.size());
        
        boolean result = subscriptionManager.unsubscribeChannel(testSessionId, testUserId, channel);
        assertTrue(result, "取消订阅应该成功");
        
        userSubs = subscriptionManager.getUserSubscriptions(testUserId);
        assertEquals(0, userSubs.size(), "用户订阅应该被清空");
    }

    /**
     * 测试登录基础订阅
     */
    @Test
    void testLoginBasicSubscription() {
        basicSubscriptionService.performLoginBasicSubscription(testSessionId, testUserId);
        
        Set<ChannelSubscription> userSubs = subscriptionManager.getUserSubscriptions(testUserId);
        assertFalse(userSubs.isEmpty(), "应该有自动订阅");
        
        boolean hasUserChannel = userSubs.stream()
            .anyMatch(sub -> ("user:" + testUserId).equals(sub.getChannel()));
        assertTrue(hasUserChannel, "应该自动订阅用户个人频道");
        
        boolean hasSystemChannel = userSubs.stream()
            .anyMatch(sub -> "system:global".equals(sub.getChannel()));
        assertTrue(hasSystemChannel, "应该自动订阅系统全局频道");
    }

    /**
     * 测试房间订阅
     */
    @Test
    void testRoomSubscription() {
        Long roomId = 789L;
        
        boolean result = basicSubscriptionService.subscribeToRoom(testSessionId, testUserId, roomId);
        assertTrue(result, "房间订阅应该成功");
        
        Set<ChannelSubscription> userSubs = subscriptionManager.getUserSubscriptions(testUserId);
        boolean hasRoomChannel = userSubs.stream()
            .anyMatch(sub -> sub.getChannel().contains("room:") && sub.getChannel().contains(roomId.toString()));
        assertTrue(hasRoomChannel, "应该订阅房间频道");
    }



    /**
     * 测试会话清理
     */
    @Test
    void testSessionCleanup() {
        String channel = "room:cleanup:test";
        subscriptionManager.subscribeChannel(
            testSessionId, testUserId, channel, Set.of("message"), "test");
        
        Set<ChannelSubscription> userSubs = subscriptionManager.getUserSubscriptions(testUserId);
        assertEquals(1, userSubs.size());
        
        Set<String> subscribers = subscriptionManager.getChannelSubscribers(channel, "message");
        assertTrue(subscribers.contains(testSessionId));
        
        subscriptionManager.cleanupSessionSubscriptions(testSessionId, testUserId);
        
        subscribers = subscriptionManager.getChannelSubscribers(channel, "message");
        assertFalse(subscribers.contains(testSessionId), "会话应该从订阅者列表中移除");
    }

    /**
     * 测试订阅统计
     */
    @Test
    void testSubscriptionStats() {
        String[] channels = {
            "room:chat:stat1",
            "room:file:stat2", 
            "user:" + testUserId
        };
        
        for (String channel : channels) {
            subscriptionManager.subscribeChannel(
                testSessionId, testUserId, channel, Set.of("message"), "test");
        }
        
        var stats = subscriptionManager.getSubscriptionStats();
        
        assertNotNull(stats);
        assertTrue(stats.containsKey("totalSubscriptions"));
        assertTrue(stats.containsKey("activeUsers"));
        assertTrue(stats.containsKey("totalChannels"));
        
        Object totalSubs = stats.get("totalSubscriptions");
        assertNotNull(totalSubs);
        assertTrue(((Number)totalSubs).intValue() >= channels.length);
    }
} 