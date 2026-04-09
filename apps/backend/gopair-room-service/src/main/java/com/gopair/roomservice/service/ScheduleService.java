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
     * 清理过期房间
     * 每15分钟执行一次，循环处理直到没有过期房间为止
     */
    @Scheduled(fixedRateString = "${gopair.schedule.room-cleanup-interval:900000}")
    @LogRecord(operation = "清理过期房间", module = "定时任务")
    public void cleanupExpiredRooms() {
        log.info("[房间服务][schedule] 开始执行过期房间清理任务");
        
        try {
            int totalCleaned = 0;
            int batchSize = 100;
            int maxIterations = 10; // 防止无限循环，最多处理 10 批
            int iteration = 0;
            
            while (iteration < maxIterations) {
                List<Room> expiredRooms = roomService.findExpiredRooms();
                
                if (expiredRooms.isEmpty()) {
                    log.info("[房间服务][schedule] 没有发现过期房间");
                    break;
                }
                
                int cleanedInBatch = 0;
                for (Room room : expiredRooms) {
                    try {
                        boolean result = roomService.deleteRoomCompletely(room.getRoomId());
                        if (result) {
                            cleanedInBatch++;
                            totalCleaned++;
                            log.info("[房间服务][schedule] 成功清理过期房间：{} ({})", room.getRoomName(), room.getRoomCode());
                        }
                    } catch (Exception e) {
                        log.error("[房间服务][schedule] 清理房间{}失败", room.getRoomId(), e);
                    }
                }
                
                log.info("[房间服务][schedule] 第{}批处理完成，本批清理{}个房间", iteration + 1, cleanedInBatch);
                
                // 如果本批处理的房间数少于 batchSize，说明已经处理完所有过期房间
                if (expiredRooms.size() < batchSize) {
                    break;
                }
                
                iteration++;
            }
            
            log.info("[房间服务][schedule] 过期房间清理任务完成，共清理{}个房间", totalCleaned);
            
        } catch (Exception e) {
            log.error("[房间服务][schedule] 执行过期房间清理任务失败", e);
        }
    }

    /**
     * 房间状态检查和维护
     * 每30分钟执行一次
     */
    @Scheduled(fixedRate = 1800000) // 30分钟
    @LogRecord(operation = "维护房间状态", module = "定时任务")
    public void maintainRoomStatus() {
        log.debug("[房间服务][schedule] 开始执行房间状态维护任务");
        
        try {
            // 这里可以添加房间状态检查逻辑
            // 例如：检查无成员的房间、更新房间统计信息等
            
            log.debug("[房间服务][schedule] 房间状态维护任务完成");
            
        } catch (Exception e) {
            log.error("[房间服务][schedule] 执行房间状态维护任务失败", e);
        }
    }
} 