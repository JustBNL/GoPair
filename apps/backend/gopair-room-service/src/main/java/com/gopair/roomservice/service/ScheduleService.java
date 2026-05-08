package com.gopair.roomservice.service;

import com.gopair.framework.logging.annotation.LogRecord;
import com.gopair.roomservice.constant.RoomConst;
import com.gopair.roomservice.domain.po.Room;
import com.gopair.roomservice.mapper.RoomMapper;
import com.gopair.roomservice.service.RoomCacheSyncService;
import com.gopair.roomservice.service.RoomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 定时任务服务
 *
 * @author gopair
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduleService {

    private final RoomMapper roomMapper;
    private final RoomService roomService;
    private final RoomCacheSyncService roomCacheSyncService;
    private final StringRedisTemplate redis;

    @Value("${gopair.schedule.room-archive-threshold-hours:24}")
    private int archiveThresholdHours;

    @PostConstruct
    public void init() {
        log.info("[房间服务][schedule] 定时任务服务初始化完成");
    }

    /**
     * 归档已关闭房间：清理资源后写入 status=3。
     * 每5分钟执行一次（由 gopair.schedule.room-cleanup-interval 配置），
     * 循环处理直到没有需要归档的房间为止。
     * room 和 room_member 永久保留，只变更 status。
     */
    @Scheduled(fixedRateString = "${gopair.schedule.room-cleanup-interval:300000}")
    @LogRecord(operation = "归档已关闭房间", module = "定时任务")
    public void cleanupClosedRooms() {
        log.info("[房间服务][schedule] 开始执行归档任务");

        try {
            int totalProcessed = 0;
            int batchSize = RoomConst.CLEANUP_BATCH_SIZE;
            int maxIterations = RoomConst.CLEANUP_MAX_ITERATIONS;
            int iteration = 0;

            while (iteration < maxIterations) {
                List<Room> roomsToArchive = roomMapper.selectRoomsToArchive(
                        LocalDateTime.now().minusHours(archiveThresholdHours));

                if (roomsToArchive.isEmpty()) {
                    log.info("[房间服务][schedule] 没有需要归档的房间");
                    break;
                }

                int processedInBatch = 0;
                for (Room room : roomsToArchive) {
                    try {
                        int count = roomService.cleanupRoomResources(room.getRoomId());
                        processedInBatch++;
                        totalProcessed++;
                        log.info("[房间服务][schedule] 成功清理房间{}的资源（消息/文件/通话），共清理约{}条", room.getRoomId(), count);

                        // 资源清理完成后，写入 status=3
                        roomMapper.updateStatus(room.getRoomId(), RoomConst.STATUS_ARCHIVED);
                        roomCacheSyncService.setStatus(room.getRoomId(), RoomConst.STATUS_ARCHIVED);
                        log.info("[房间服务][schedule] 房间{}已归档", room.getRoomId());
                    } catch (Exception e) {
                        log.error("[房间服务][schedule] 归档房间{}失败", room.getRoomId(), e);
                    }
                }

                log.info("[房间服务][schedule] 第{}批归档完成，本批处理{}个房间", iteration + 1, processedInBatch);

                if (roomsToArchive.size() < batchSize) {
                    break;
                }

                iteration++;
            }

            log.info("[房间服务][schedule] 归档任务完成，共处理{}个房间", totalProcessed);

        } catch (Exception e) {
            log.error("[房间服务][schedule] 执行归档任务失败", e);
        }
    }

    /**
     * 房间状态检查和维护：过期检测 + 超时过期房间系统关闭。
     * 每5分钟执行一次（由 gopair.schedule.room-cleanup-interval 配置）。
     *
     * <ol>
     *   <li>检测 ACTIVE 房间是否已过期：expire_time < now → status 改为 2</li>
     *   <li>检测 EXPIRED 房间是否超时归档前置期：expire_time < now - 30天 → status 改为 1（系统关闭）</li>
     * </ol>
     */
    @Scheduled(fixedRateString = "${gopair.schedule.room-cleanup-interval:300000}")
    @LogRecord(operation = "维护房间状态", module = "定时任务")
    public void maintainRoomStatus() {
        log.debug("[房间服务][schedule] 开始执行房间状态维护任务");

        try {
            // Step 1: ACTIVE → EXPIRED
            int expiredCount = processActiveToExpired();

            // Step 2: EXPIRED → CLOSED（系统关闭）
            int closedCount = processExpiredToClosed();

            log.debug("[房间服务][schedule] 房间状态维护完成：过期={}，系统关闭={}", expiredCount, closedCount);

        } catch (Exception e) {
            log.error("[房间服务][schedule] 执行房间状态维护任务失败", e);
        }
    }

    /**
     * 将所有 status=0 且 expire_time < now 的房间改为 EXPIRED（status=2）。
     */
    private int processActiveToExpired() {
        int total = 0;
        int batchSize = RoomConst.CLEANUP_BATCH_SIZE;

        while (true) {
            List<Room> expiredRooms = roomMapper.selectExpiredRooms(LocalDateTime.now());
            if (expiredRooms.isEmpty()) {
                break;
            }
            for (Room room : expiredRooms) {
                try {
                    roomService.expireRoom(room.getRoomId());
                    total++;
                } catch (Exception e) {
                    log.warn("[房间服务][schedule] 房间{}过期处理失败", room.getRoomId(), e);
                }
            }
            if (expiredRooms.size() < batchSize) {
                break;
            }
        }
        return total;
    }

    /**
     * 将所有 status=2 且 expire_time < now - EXPIRED_TO_CLOSED_DAYS 天 的房间改为 CLOSED（status=1）。
     */
    private int processExpiredToClosed() {
        int total = 0;
        int batchSize = RoomConst.CLEANUP_BATCH_SIZE;

        while (true) {
            LocalDateTime threshold = LocalDateTime.now().minusDays(RoomConst.EXPIRED_TO_CLOSED_DAYS);
            List<Room> toClose = roomMapper.selectExpiredRoomsToClose(threshold);
            if (toClose.isEmpty()) {
                break;
            }
            for (Room room : toClose) {
                try {
                    // 系统关闭，不检查权限，operaterId 传 null
                    roomService.systemCloseRoom(room.getRoomId());
                    total++;
                } catch (Exception e) {
                    log.warn("[房间服务][schedule] 房间{}系统关闭失败", room.getRoomId(), e);
                }
            }
            if (toClose.size() < batchSize) {
                break;
            }
        }
        return total;
    }

    /**
     * 回收超时预占。
     * 由配置 gopair.room.reservation.reaper-interval-ms 控制执行频率，默认60秒。
     * 用 SCAN 替代 KEYS，避免阻塞 Redis。
     */
    @Scheduled(fixedDelayString = "${gopair.room.reservation.reaper-interval-ms:60000}")
    @LogRecord(operation = "回收超时预占", module = "定时任务")
    public void reapTimeoutReservations() {
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
