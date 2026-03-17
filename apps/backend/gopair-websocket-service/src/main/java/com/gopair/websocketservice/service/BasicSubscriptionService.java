package com.gopair.websocketservice.service;

import com.gopair.websocketservice.constants.WebSocketConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Set;

/**
 * 基础订阅服务
 *
 * 负责在用户成功建立 WebSocket 连接后，为其执行一组「登录即订阅」的基础频道订阅。
 * 这些订阅仅是对 {@link SubscriptionManagerService} 的封装，以保持原有语义不变。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BasicSubscriptionService {

    private final SubscriptionManagerService subscriptionManagerService;

    /**
     * 为新登录用户执行基础订阅。
     *
     * 当前实现仅包含与用户强相关的基础频道以及系统广播频道，
     * 以尽量贴近原有 BasicSubscriptionService 的语义，同时避免改变外部行为。
     *
     * @param sessionId WebSocket 会话 ID
     * @param userId    用户 ID
     */
    public void performLoginBasicSubscription(String sessionId, Long userId) {
        if (sessionId == null || userId == null) {
            log.warn("[基础订阅] 跳过基础订阅，sessionId 或 userId 为空: sessionId={}, userId={}", sessionId, userId);
            return;
        }

        try {
            // 用户个人频道，例如 user:{userId}
            String userChannel = WebSocketConstants.CHANNEL_PREFIX_USER + userId;
            subscribeSilently(sessionId, userId, userChannel, Collections.emptySet(), "auto");

            // 系统广播频道，例如 system:global
            String systemGlobalChannel = WebSocketConstants.CHANNEL_PREFIX_SYSTEM + "global";
            subscribeSilently(sessionId, userId, systemGlobalChannel, Collections.emptySet(), "auto");

            log.info("[基础订阅] 登录基础订阅完成: sessionId={}, userId={}", sessionId, userId);
        } catch (Exception e) {
            log.error("[基础订阅] 执行登录基础订阅失败: sessionId={}, userId={}", sessionId, userId, e);
        }
    }

    /**
     * 为用户订阅指定房间的核心频道。
     *
     * 当前采用统一的房间频道前缀 {@code room:}，并在频道中包含房间 ID，
     * 以满足测试中对频道格式的断言要求。
     *
     * @param sessionId WebSocket 会话 ID
     * @param userId    用户 ID
     * @param roomId    房间 ID
     * @return true 表示订阅成功，false 表示订阅失败或参数不合法
     */
    public boolean subscribeToRoom(String sessionId, Long userId, Long roomId) {
        if (sessionId == null || userId == null || roomId == null) {
            log.warn("[基础订阅] 房间订阅参数不合法: sessionId={}, userId={}, roomId={}",
                    sessionId, userId, roomId);
            return false;
        }

        try {
            String channel = WebSocketConstants.CHANNEL_PREFIX_ROOM + "chat:" + roomId;
            boolean result = subscriptionManagerService.subscribeChannel(
                    sessionId, userId, channel, Collections.emptySet(), "manual");
            if (!result) {
                log.warn("[基础订阅] 房间订阅失败: sessionId={}, userId={}, roomId={}, channel={}",
                        sessionId, userId, roomId, channel);
            }
            return result;
        } catch (Exception e) {
            log.error("[基础订阅] 房间订阅异常: sessionId={}, userId={}, roomId={}",
                    sessionId, userId, roomId, e);
            return false;
        }
    }

    private void subscribeSilently(String sessionId,
                                   Long userId,
                                   String channel,
                                   Set<String> eventTypes,
                                   String source) {
        boolean success = subscriptionManagerService.subscribeChannel(sessionId, userId, channel, eventTypes, source);
        if (!success) {
            log.warn("[基础订阅] 基础订阅失败: sessionId={}, userId={}, channel={}", sessionId, userId, channel);
        }
    }
}

