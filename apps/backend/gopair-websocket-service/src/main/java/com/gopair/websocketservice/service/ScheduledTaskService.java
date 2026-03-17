package com.gopair.websocketservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduledTaskService {

    private final RedisOperationService redisOperationService;
    private final SubscriptionManagerService subscriptionManagerService;

    @Scheduled(fixedRate = 60000)
    public void lightweightRedisCheckTask() {
        log.debug("[定时任务] 开始执行轻量级Redis检查...");
        redisOperationService.performLightweightRedisCheck();
    }

    @Scheduled(fixedRate = 3600000)
    public void hourlyRedisCleanupTask() {
        log.info("[定时任务] 开始执行每小时Redis清理...");
        redisOperationService.performHourlyRedisCleanup();
    }
    
    @Scheduled(fixedRate = 3600000)
    public void cleanupExpiredPermissions() {
        log.debug("[定时任务] 开始执行权限缓存清理...");
        subscriptionManagerService.performExpiredPermissionsCleanup();
    }

    @Scheduled(cron = "0 0 2 * * ?")
    public void dailyDeepRedisCleanupTask() {
        log.info("[定时任务] 开始执行每日深度Redis清理...");
        redisOperationService.performDailyDeepRedisCleanup();
    }
    
    @Scheduled(cron = "0 0 3 * * ?")
    public void dailySubscriptionCleanupTask() {
        log.info("[定时任务] 开始执行每日订阅清理...");
        subscriptionManagerService.performDailySubscriptionCleanup();
    }
}
