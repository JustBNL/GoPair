package com.gopair.roomservice.task;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

@Slf4j
@Component
public class PendingReservationReaper {

    private final StringRedisTemplate redis;

    public PendingReservationReaper(StringRedisTemplate redis) {
        this.redis = redis;
    }

    // 默认由配置 gopair.room.reservation.reaper-interval-ms 控制
    @Scheduled(fixedDelayString = "${gopair.room.reservation.reaper-interval-ms:60000}")
    public void reap() {
        try {
            Set<String> pendingKeys = redis.keys("room:*:pending");
            if (pendingKeys == null || pendingKeys.isEmpty()) {
                return;
            }
            for (String pKey : pendingKeys) {
                Map<Object, Object> entries = redis.opsForHash().entries(pKey);
                if (entries == null || entries.isEmpty()) {
                    continue;
                }
                // derive roomId from key: room:{roomId}:pending
                String roomId = extractRoomId(pKey);
                if (roomId == null) {
                    continue;
                }
                String metaKey = "room:" + roomId + ":meta";
                for (Map.Entry<Object, Object> e : entries.entrySet()) {
                    String userId = String.valueOf(e.getKey());
                    String token = String.valueOf(e.getValue());
                    String tokenKey = "join:" + token;
                    String status = redis.opsForValue().get(tokenKey);
                    if (status == null || "FAILED".equalsIgnoreCase(status)) {
                        try {
                            // best-effort: reserved-- if > 0
                            String reserved = String.valueOf(redis.opsForHash().get(metaKey, "reserved"));
                            int r = 0;
                            try { r = Integer.parseInt(reserved); } catch (Exception ignore) {}
                            if (r > 0) {
                                redis.opsForHash().increment(metaKey, "reserved", -1);
                            }
                            redis.opsForHash().delete(pKey, userId);
                        } catch (Exception ignore) {
                        }
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("[房间服务][join-async] 清理挂起预占任务异常", ex);
        }
    }

    private String extractRoomId(String key) {
        // room:{roomId}:pending
        try {
            int s = key.indexOf(":");
            int e = key.lastIndexOf(":");
            if (s > 0 && e > s) {
                return key.substring(s + 1, e);
            }
        } catch (Exception ignore) {}
        return null;
    }
} 