package com.gopair.roomservice.service;

import com.gopair.framework.logging.annotation.LogRecord;
import com.gopair.roomservice.domain.po.Room;
import com.gopair.roomservice.service.RoomService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.List;

/**
 * 定时任务服务
 *
 * @author gopair
 */
@Slf4j
@Component
public class ScheduleService {

    private final RoomService roomService;

    public ScheduleService(RoomService roomService) {
        this.roomService = roomService;
    }

    @PostConstruct
    public void init() {
        log.info("[房间服务][schedule] 定时任务服务初始化完成");
    }

    /**
     * 清理已关闭房间的资源（消息、文件、语音通话）。
     * 每15分钟执行一次，循环处理直到没有需要清理的房间为止。
     * room 和 room_member 永久保留，不在此定时任务中删除。
     */
    @Scheduled(fixedRateString = "${gopair.schedule.room-cleanup-interval:900000}")
    @LogRecord(operation = "清理关闭房间资源", module = "定时任务")
    public void cleanupClosedRooms() {
        log.info("[房间服务][schedule] 开始执行关闭房间资源清理任务");

        try {
            int totalProcessed = 0;
            int batchSize = 100;
            int maxIterations = 10;
            int iteration = 0;

            while (iteration < maxIterations) {
                List<Room> roomsToClean = roomService.findRoomsToClean();

                if (roomsToClean.isEmpty()) {
                    log.info("[房间服务][schedule] 没有需要清理的房间");
                    break;
                }

                int processedInBatch = 0;
                for (Room room : roomsToClean) {
                    try {
                        int count = roomService.cleanupRoomResources(room.getRoomId());
                        processedInBatch++;
                        totalProcessed++;
                        log.info("[房间服务][schedule] 成功清理房间{}的资源（消息/文件/通话），共清理约{}条", room.getRoomId(), count);
                    } catch (Exception e) {
                        log.error("[房间服务][schedule] 清理房间{}资源失败", room.getRoomId(), e);
                    }
                }

                log.info("[房间服务][schedule] 第{}批处理完成，本批处理{}个房间", iteration + 1, processedInBatch);

                if (roomsToClean.size() < batchSize) {
                    break;
                }

                iteration++;
            }

            log.info("[房间服务][schedule] 关闭房间资源清理任务完成，共处理{}个房间", totalProcessed);

        } catch (Exception e) {
            log.error("[房间服务][schedule] 执行清理任务失败", e);
        }
    }

    /**
     * 房间状态检查和维护
     * 每30分钟执行一次
     */
    @Scheduled(fixedRate = 1800000)
    @LogRecord(operation = "维护房间状态", module = "定时任务")
    public void maintainRoomStatus() {
        log.debug("[房间服务][schedule] 开始执行房间状态维护任务");

        try {
            log.debug("[房间服务][schedule] 房间状态维护任务完成");

        } catch (Exception e) {
            log.error("[房间服务][schedule] 执行房间状态维护任务失败", e);
        }
    }
}
