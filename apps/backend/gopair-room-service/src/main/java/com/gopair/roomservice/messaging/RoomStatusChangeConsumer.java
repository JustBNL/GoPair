package com.gopair.roomservice.messaging;

import com.gopair.common.constants.SystemConstants;
import com.gopair.common.domain.event.RoomStatusChangeEvent;
import com.gopair.roomservice.constant.RoomConst;
import com.gopair.roomservice.service.RoomCacheSyncService;
import com.gopair.common.service.WebSocketMessageProducer;
import com.gopair.framework.logging.annotation.LogRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * 房间状态变更事件消费者，处理来自 admin-service 的禁用/解禁事件。
 * admin-service 直接操作 DB 更新 room 表的 status，事件用于触发 Redis 同步和 WebSocket 通知。
 *
 * * [执行链路]
 * 1. DISABLE：同步 Redis status=4 + 广播 room_disabled + 终止语音通话。
 * 2. ENABLE：同步 Redis status=0 + 广播 room_enabled。
 *
 * @param event 房间状态变更事件
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RoomStatusChangeConsumer {

    private final RoomCacheSyncService roomCacheSyncService;
    private final WebSocketMessageProducer wsProducer;
    private final RestTemplate restTemplate;

    @Transactional(rollbackFor = Exception.class)
    @RabbitListener(queues = SystemConstants.QUEUE_ROOM_STATUS_CHANGE)
    @LogRecord(operation = "消费房间状态变更事件", module = "消息消费")
    public void handle(RoomStatusChangeEvent event) {
        Long roomId = event.getRoomId();
        RoomStatusChangeEvent.Action action = event.getAction();
        log.info("[房间服务][status-change] 处理房间状态变更 event={} roomId={} action={}",
                event, roomId, action);

        try {
            if (action == RoomStatusChangeEvent.Action.DISABLE) {
                handleDisable(roomId);
            } else if (action == RoomStatusChangeEvent.Action.ENABLE) {
                handleEnable(roomId);
            }
        } catch (Exception e) {
            log.error("[房间服务][status-change] 处理房间状态变更失败 roomId={} action={}", roomId, action, e);
            throw e;
        }
    }

    private void handleDisable(Long roomId) {
        try {
            roomCacheSyncService.setStatus(roomId, RoomConst.STATUS_DISABLED);
        } catch (Exception e) {
            log.warn("[房间服务][status-change] Redis 同步 DISABLED 状态失败 roomId={}", roomId, e);
        }

        try {
            wsProducer.sendEventToRoom(roomId, "room_disabled", Map.of(
                    "roomId", roomId,
                    "timestamp", System.currentTimeMillis()
            ));
        } catch (Exception e) {
            log.warn("[房间服务][status-change] 发送 room_disabled 事件失败 roomId={}", roomId, e);
        }

        try {
            restTemplate.postForObject(
                    RoomConst.VOICE_SERVICE_END_ALL_URL + roomId + "/end-all", null, Integer.class);
            log.info("[房间服务][status-change] 禁用房间{}语音通话已终止", roomId);
        } catch (Exception e) {
            log.warn("[房间服务][status-change] 禁用房间{}终止语音通话失败: {}", roomId, e.getMessage());
        }
    }

    private void handleEnable(Long roomId) {
        try {
            roomCacheSyncService.setStatus(roomId, RoomConst.STATUS_ACTIVE);
        } catch (Exception e) {
            log.warn("[房间服务][status-change] Redis 同步 ACTIVE 状态失败 roomId={}", roomId, e);
        }

        try {
            wsProducer.sendEventToRoom(roomId, "room_enabled", Map.of(
                    "roomId", roomId,
                    "timestamp", System.currentTimeMillis()
            ));
        } catch (Exception e) {
            log.warn("[房间服务][status-change] 发送 room_enabled 事件失败 roomId={}", roomId, e);
        }
    }
}
