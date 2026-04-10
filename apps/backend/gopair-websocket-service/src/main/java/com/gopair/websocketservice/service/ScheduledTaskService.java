package com.gopair.websocketservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import java.time.LocalDateTime;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduledTaskService {

    private final RedisOperationService redisOperationService;
    private final SubscriptionManagerService subscriptionManagerService;
    private final ConnectionManagerService connectionManager;

    /** 心跳超时阈值（秒），超过此时间未收到心跳视为超时 */
    private static final long HEARTBEAT_TIMEOUT_SECONDS = 90L;
    /** 轻量级检查间隔：1 分钟 */
    private static final long LIGHTWEIGHT_CHECK_INTERVAL_MS = 60000L;
    /** 心跳超时检测间隔：30 秒 */
    private static final long HEARTBEAT_CHECK_INTERVAL_MS = 30000L;
    /** 每小时清理间隔：1 小时 */
    private static final long HOURLY_CLEANUP_INTERVAL_MS = 3600000L;

    @Scheduled(fixedRate = LIGHTWEIGHT_CHECK_INTERVAL_MS)
    public void lightweightRedisCheckTask() {
        log.debug("[定时任务] 开始执行轻量级Redis检查...");
        try {
            redisOperationService.performLightweightRedisCheck();
        } catch (Exception e) {
            log.warn("[定时任务] 轻量级Redis检查失败: {}", e.getMessage());
        }
    }

    /**
     * 检测心跳超时的 WebSocket 会话。
     *
     * 遍历本地内存中的所有活跃会话，比对 Redis 中记录的最近活跃时间。
     * 若超过 HEARTBEAT_TIMEOUT_SECONDS（90秒）未收到心跳，
     * 则通过连接管理服务强制关闭会话并触发离线流程。
     *
     * 注意：由于仅扫描本地 sessions Map，多实例部署下每个实例只检测自己的会话，
     * 不需要分布式锁。Redis 中的 lastActiveTime 由 {@link MessageHandler#handleHeartbeatMessage}
     * 在每次收到心跳时更新。
     */
    @Scheduled(fixedRate = HEARTBEAT_CHECK_INTERVAL_MS)
    public void heartbeatTimeoutDetectionTask() {
        try {
            long now = System.currentTimeMillis() / 1000;
            Set<String> allSessionIds = connectionManager.getAllSessionIds();

            for (String sessionId : allSessionIds) {
                LocalDateTime lastActive = connectionManager.getSessionLastActiveTime(sessionId);
                if (lastActive == null) {
                    continue;
                }
                long lastActiveEpoch = lastActive.toEpochSecond(java.time.ZoneOffset.UTC);
                if (now - lastActiveEpoch > HEARTBEAT_TIMEOUT_SECONDS) {
                    log.warn("[定时任务] 检测到心跳超时: sessionId={}, lastActive={}, timeout={}s",
                            sessionId, lastActive, HEARTBEAT_TIMEOUT_SECONDS);
                    WebSocketSession session = connectionManager.getSession(sessionId);
                    if (session != null && session.isOpen()) {
                        try {
                            session.close(org.springframework.web.socket.CloseStatus.GOING_AWAY);
                        } catch (Exception e) {
                            log.debug("[定时任务] 关闭超时会话失败（可能已关闭）: sessionId={}", sessionId);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[定时任务] 心跳超时检测任务失败: {}", e.getMessage());
        }
    }

    @Scheduled(fixedRate = HOURLY_CLEANUP_INTERVAL_MS)
    public void hourlyRedisCleanupTask() {
        log.info("[定时任务] 开始执行每小时Redis清理...");
        try {
            redisOperationService.performHourlyRedisCleanup();
        } catch (Exception e) {
            log.warn("[定时任务] 每小时Redis清理失败: {}", e.getMessage());
        }
    }
    
    @Scheduled(fixedRate = HOURLY_CLEANUP_INTERVAL_MS)
    public void cleanupExpiredPermissions() {
        log.debug("[定时任务] 开始执行权限缓存清理...");
        try {
            subscriptionManagerService.performExpiredPermissionsCleanup();
        } catch (Exception e) {
            log.warn("[定时任务] 权限缓存清理失败: {}", e.getMessage());
        }
    }

    @Scheduled(cron = "0 0 2 * * ?")
    public void dailyDeepRedisCleanupTask() {
        log.info("[定时任务] 开始执行每日深度Redis清理...");
        try {
            redisOperationService.performDailyDeepRedisCleanup();
        } catch (Exception e) {
            log.warn("[定时任务] 每日深度Redis清理失败: {}", e.getMessage());
        }
    }
    
    @Scheduled(cron = "0 0 3 * * ?")
    public void dailySubscriptionCleanupTask() {
        log.info("[定时任务] 开始执行每日订阅清理...");
        try {
            subscriptionManagerService.performDailySubscriptionCleanup();
        } catch (Exception e) {
            log.warn("[定时任务] 每日订阅清理失败: {}", e.getMessage());
        }
    }
}
