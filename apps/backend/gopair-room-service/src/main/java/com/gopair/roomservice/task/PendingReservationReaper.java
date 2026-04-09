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
                    // 只清理明确失败的 pending：
                    // - token 存在且以 FAILED 结尾（新格式 roomId:userId:FAILED 或旧格式 FAILED）
                    // - token 为 null 时表示 PROCESSING TTL 尚未到期或 Consumer 已处理完（不应误清理）
                    boolean shouldClean = value != null && value.toUpperCase().endsWith(RoomConst.JOIN_RESULT_FAILED);
                    if (shouldClean) {
                        try {
                            String reserved = String.valueOf(redis.opsForHash().get(metaKey, RoomConst.FIELD_RESERVED));
                            int r = 0;
                            try { r = Integer.parseInt(reserved); } catch (Exception ignore) {}
                            if (r > 0) {
                                redis.opsForHash().increment(metaKey, RoomConst.FIELD_RESERVED, -1);
                            }
                            redis.opsForHash().delete(pKey, userId);
                            log.debug("[房间服务][reaper] 清理失败预占 房间={} 用户={} token={}", roomId, userId, token);
                        } catch (Exception ignore) {
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
