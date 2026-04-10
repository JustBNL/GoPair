package com.gopair.roomservice.task;

import com.gopair.framework.logging.annotation.LogRecord;
import com.gopair.roomservice.constant.RoomConst;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class PendingReservationReaper {

    private final StringRedisTemplate redis;

    public PendingReservationReaper(StringRedisTemplate redis) {
        this.redis = redis;
    }

    // 默认由配置 gopair.room.reservation.reaper-interval-ms 控制
    @Scheduled(fixedDelayString = "${gopair.room.reservation.reaper-interval-ms:60000}")
    @LogRecord(operation = "回收超时预占", module = "定时任务")
    public void reap() {
        // 用 SCAN 替代 KEYS，避免阻塞 Redis
        List<String> pendingKeys = scanPendingKeys();
        if (pendingKeys.isEmpty()) {
            return;
        }
        for (String pKey : pendingKeys) {
            try {
                Map<Object, Object> entries = redis.opsForHash().entries(pKey);
                if (entries == null || entries.isEmpty()) {
                    continue;
                }
                String roomId = extractRoomId(pKey);
                if (roomId == null) {
                    continue;
                }
                String metaKey = RoomConst.metaKey(Long.parseLong(roomId));
                for (Map.Entry<Object, Object> e : entries.entrySet()) {
                    String userId = String.valueOf(e.getKey());
                    String token  = String.valueOf(e.getValue());
                    String value  = redis.opsForValue().get(RoomConst.joinTokenKey(token));
                    // 清理条件：
                    // 1. token 存在且明确标记为 FAILED（join 因满员/超时等原因失败）
                    // 2. token 不存在（TTL 已过期，结果已被 Redis 自动清理），说明 join 未正常完成，pending 残留需回收
                    boolean shouldClean = (value != null && value.toUpperCase().endsWith(RoomConst.JOIN_RESULT_FAILED))
                            || (value == null);
                    if (shouldClean) {
                        try {
                            String reserved = String.valueOf(redis.opsForHash().get(metaKey, RoomConst.FIELD_RESERVED));
                            int r = 0;
                            try { r = Integer.parseInt(reserved); } catch (Exception ignore) {}
                            if (r > 0) {
                                redis.opsForHash().increment(metaKey, RoomConst.FIELD_RESERVED, -1);
                            }
                            redis.opsForHash().delete(pKey, userId);
                            log.debug("[房间服务][reaper] 清理预占 房间={} 用户={} token={} 原因={}", roomId, userId, token,
                                    value == null ? "token已过期" : "join失败");
                        } catch (Exception ex) {
                            log.warn("[房间服务][reaper] 清理预占失败 房间={} 用户={} token={} 错误={}", roomId, userId, token, ex.getMessage());
                        }
                    }
                }
            } catch (Exception ex) {
                log.warn("[房间服务][reaper] 处理 key={} 异常", pKey, ex);
            }
        }
    }

    private List<String> scanPendingKeys() {
        List<String> result = new ArrayList<>();
        ScanOptions options = ScanOptions.scanOptions().match(RoomConst.PATTERN_ROOM_PENDING).count(100).build();
        try (Cursor<String> cursor = redis.scan(options)) {
            while (cursor.hasNext()) {
                result.add(cursor.next());
            }
        } catch (Exception ex) {
            log.warn("[房间服务][reaper] SCAN 扫描 pending key 异常", ex);
        }
        return result;
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
