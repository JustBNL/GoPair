package com.gopair.websocketservice.service;

import com.gopair.websocketservice.domain.ChannelSubscription;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * 基础订阅服务
 * 简化的订阅管理实现，只保留核心订阅功能
 * 
 * @author gopair
 */
@Slf4j
@Service
public class BasicSubscriptionService {

    private final SubscriptionManagerService subscriptionManager;
    private final ConnectionManagerService connectionManager;
    
    public BasicSubscriptionService(SubscriptionManagerService subscriptionManager, 
                                  @Lazy ConnectionManagerService connectionManager) {
        this.subscriptionManager = subscriptionManager;
        this.connectionManager = connectionManager;
    }

    /**
     * 执行登录基础订阅
     * 简化版本，只处理基本的用户频道订阅
     * 
     * @param sessionId 会话ID
     * @param userId 用户ID
     */
    public void performLoginBasicSubscription(String sessionId, Long userId) {
        try {
            log.info("[订阅管理] 开始执行登录基础订阅: sessionId={}, userId={}", sessionId, userId);

            // 1. 订阅用户私人频道（接收个人消息）
            String userChannel = "user:" + userId;
            subscriptionManager.subscribeChannel(sessionId, userId, userChannel, 
                    Set.of("message", "notification"), "auto");
            
            log.info("[订阅管理] 用户私人频道订阅成功: userId={}, channel={}", userId, userChannel);

            // 2. 订阅全局系统频道（接收系统通知）
            String systemChannel = "system:global";
            subscriptionManager.subscribeChannel(sessionId, userId, systemChannel, 
                    Set.of("announcement", "maintenance"), "auto");
            
            log.info("[订阅管理] 系统频道订阅成功: userId={}, channel={}", userId, systemChannel);

            log.info("[订阅管理] 登录基础订阅完成: sessionId={}, userId={}", sessionId, userId);

        } catch (Exception e) {
            log.error("[订阅管理] 登录基础订阅失败: sessionId={}, userId={}", sessionId, userId, e);
        }
    }

    /**
     * 订阅房间频道
     * 
     * @param sessionId 会话ID
     * @param userId 用户ID
     * @param roomId 房间ID
     * @return 是否订阅成功
     */
    public boolean subscribeToRoom(String sessionId, Long userId, Long roomId) {
        try {
            log.info("[订阅管理] 开始订阅房间频道: sessionId={}, userId={}, roomId={}", 
                    sessionId, userId, roomId);

            // 订阅房间频道，接收所有房间事件
            String roomChannel = "room:" + roomId;
            boolean success = subscriptionManager.subscribeChannel(sessionId, userId, roomChannel, 
                    Set.of("chat", "file", "voice", "member"), "manual");

            if (success) {
                log.info("[订阅管理] 房间频道订阅成功: userId={}, roomId={}, channel={}", 
                        userId, roomId, roomChannel);
            } else {
                log.warn("[订阅管理] 房间频道订阅失败: userId={}, roomId={}", userId, roomId);
            }

            return success;

        } catch (Exception e) {
            log.error("[订阅管理] 订阅房间频道异常: sessionId={}, userId={}, roomId={}", 
                    sessionId, userId, roomId, e);
            return false;
        }
    }

    /**
     * 取消订阅房间频道
     * 
     * @param sessionId 会话ID
     * @param userId 用户ID
     * @param roomId 房间ID
     * @return 是否取消成功
     */
    public boolean unsubscribeFromRoom(String sessionId, Long userId, Long roomId) {
        try {
            log.info("[订阅管理] 开始取消订阅房间频道: sessionId={}, userId={}, roomId={}", 
                    sessionId, userId, roomId);

            String roomChannel = "room:" + roomId;
            boolean success = subscriptionManager.unsubscribeChannel(sessionId, userId, roomChannel);

            if (success) {
                log.info("[订阅管理] 房间频道取消订阅成功: userId={}, roomId={}", userId, roomId);
            } else {
                log.warn("[订阅管理] 房间频道取消订阅失败: userId={}, roomId={}", userId, roomId);
            }

            return success;

        } catch (Exception e) {
            log.error("[订阅管理] 取消订阅房间频道异常: sessionId={}, userId={}, roomId={}", 
                    sessionId, userId, roomId, e);
            return false;
        }
    }

    /**
     * 订阅语音通话频道
     * 
     * @param sessionId 会话ID
     * @param userId 用户ID
     * @param callId 通话ID
     * @return 是否订阅成功
     */
    public boolean subscribeToVoiceCall(String sessionId, Long userId, String callId) {
        try {
            log.info("[订阅管理] 开始订阅语音通话频道: sessionId={}, userId={}, callId={}", 
                    sessionId, userId, callId);

            String voiceChannel = "voice:" + callId;
            boolean success = subscriptionManager.subscribeChannel(sessionId, userId, voiceChannel, 
                    Set.of("signaling", "control"), "manual");

            if (success) {
                log.info("[订阅管理] 语音通话频道订阅成功: userId={}, callId={}", userId, callId);
            } else {
                log.warn("[订阅管理] 语音通话频道订阅失败: userId={}, callId={}", userId, callId);
            }

            return success;

        } catch (Exception e) {
            log.error("[订阅管理] 订阅语音通话频道异常: sessionId={}, userId={}, callId={}", 
                    sessionId, userId, callId, e);
            return false;
        }
    }

    /**
     * 取消订阅语音通话频道
     * 
     * @param sessionId 会话ID
     * @param userId 用户ID
     * @param callId 通话ID
     * @return 是否取消成功
     */
    public boolean unsubscribeFromVoiceCall(String sessionId, Long userId, String callId) {
        try {
            log.info("[订阅管理] 开始取消订阅语音通话频道: sessionId={}, userId={}, callId={}", 
                    sessionId, userId, callId);

            String voiceChannel = "voice:" + callId;
            boolean success = subscriptionManager.unsubscribeChannel(sessionId, userId, voiceChannel);

            if (success) {
                log.info("[订阅管理] 语音通话频道取消订阅成功: userId={}, callId={}", userId, callId);
            } else {
                log.warn("[订阅管理] 语音通话频道取消订阅失败: userId={}, callId={}", userId, callId);
            }

            return success;

        } catch (Exception e) {
            log.error("[订阅管理] 取消订阅语音通话频道异常: sessionId={}, userId={}, callId={}", 
                    sessionId, userId, callId, e);
            return false;
        }
    }

    /**
     * 获取用户当前订阅数量
     * 
     * @param userId 用户ID
     * @return 订阅数量
     */
    public int getUserSubscriptionCount(Long userId) {
        try {
            int count = subscriptionManager.getUserSubscriptions(userId).size();
            log.debug("[订阅管理] 获取用户订阅数量: userId={}, count={}", userId, count);
            return count;
        } catch (Exception e) {
            log.error("[订阅管理] 获取用户订阅数量失败: userId={}", userId, e);
            return 0;
        }
    }

    /**
     * 清理用户所有订阅
     * 
     * @param sessionId 会话ID
     * @param userId 用户ID
     */
    public void cleanupUserSubscriptions(String sessionId, Long userId) {
        try {
            log.info("[订阅管理] 开始清理用户订阅: sessionId={}, userId={}", sessionId, userId);
            
            // 获取用户所有订阅，逐一取消
            Set<ChannelSubscription> userSubs = subscriptionManager.getUserSubscriptions(userId);
            for (ChannelSubscription subscription : userSubs) {
                subscriptionManager.unsubscribeChannel(sessionId, userId, subscription.getChannel());
            }
            
            log.info("[订阅管理] 用户订阅清理完成: sessionId={}, userId={}, count={}", 
                    sessionId, userId, userSubs.size());
            
        } catch (Exception e) {
            log.error("[订阅管理] 清理用户订阅失败: sessionId={}, userId={}", sessionId, userId, e);
        }
    }


} 