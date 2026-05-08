package com.gopair.roomservice.messaging;

import com.gopair.common.constants.SystemConstants;
import com.gopair.roomservice.constant.RoomConst;
import com.gopair.roomservice.domain.event.MemberRemovalEvent;
import com.gopair.roomservice.enums.LeaveTypeEnum;
import com.gopair.roomservice.domain.po.Room;
import com.gopair.roomservice.mapper.RoomMapper;
import com.gopair.roomservice.service.MemberRemovalService;
import com.gopair.roomservice.service.RoomMemberService;
import com.gopair.common.service.WebSocketMessageProducer;
import com.gopair.framework.logging.annotation.LogRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 成员移除事件消费者，leaveRoom / kickMember / closeRoom 共用。
 *
 * * [核心策略]
 * - 幂等：pending_removal 作为一次性令牌，只处理存在且匹配的事件。
 * - leaveType=ROOM_CLOSED 特殊处理：批量标记所有成员，不写 pending_removal（无特定 userId）。
 *
 * * [执行链路]
 * 1. ROOM_CLOSED：直接 markAllAsLeft + 自动关房检查。
 * 2. VOLUNTARY/KICKED：检查 pending_removal → markAsLeft → 递减人数 → 清理 pending → WebSocket。
 * 3. 若房间人数为 0 且状态仍为活跃，尝试关闭房间。
 *
 * @param event 成员移除事件
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemberRemovalConsumer {

    private final RoomMapper roomMapper;
    private final RoomMemberService roomMemberService;
    private final MemberRemovalService memberRemovalService;
    private final StringRedisTemplate stringRedisTemplate;
    private final WebSocketMessageProducer wsProducer;

    @Transactional(rollbackFor = Exception.class)
    @RabbitListener(queues = SystemConstants.QUEUE_ROOM_LEAVE)
    @LogRecord(operation = "消费成员移除事件", module = "消息消费")
    public void handle(MemberRemovalEvent event) {
        Long roomId = event.getRoomId();
        LeaveTypeEnum leaveType = event.getLeaveType();
        log.info("[房间服务][removal] 处理成员移除事件 roomId={} userId={} leaveType={}",
                roomId, event.getUserId(), leaveType);

        try {
            if (leaveType == LeaveTypeEnum.ROOM_CLOSED) {
                // ROOM_CLOSED：closeRoom 触发，批量标记所有成员被动离开
                roomMemberService.markAllAsLeft(roomId, LeaveTypeEnum.ROOM_CLOSED.getValue());
                checkAndCloseRoom(roomId);
                return;
            }

            Long userId = event.getUserId();
            if (userId == null) {
                log.warn("[房间服务][removal] leaveType={} 但 userId 为 null，跳过 roomId={}", leaveType, roomId);
                return;
            }

            // 检查 pending_removal 是否存在（Lua 成功写入才处理）
            Boolean exists = stringRedisTemplate.opsForHash()
                    .hasKey(RoomConst.pendingRemovalKey(roomId), String.valueOf(userId));
            if (exists == null || !exists) {
                log.info("[房间服务][removal] pending_removal 不存在，可能已处理或 Lua 失败，跳过 roomId={} userId={}",
                        roomId, userId);
                return;
            }

            // DB 持久化
            boolean marked = roomMemberService.markAsLeft(roomId, userId, leaveType.getValue());
            if (marked) {
                roomMapper.decrementMembersIfPositive(roomId);
            }

            // 清理 pending_removal
            stringRedisTemplate.opsForHash().delete(RoomConst.pendingRemovalKey(roomId), String.valueOf(userId));

            // WebSocket 通知
            sendWebSocketNotification(roomId, userId, leaveType, event.getOperatorId());

            // 检查是否需要自动关房
            checkAndCloseRoom(roomId);

        } catch (Exception e) {
            log.error("[房间服务][removal] 处理异常 roomId={} userId={}", roomId, event.getUserId(), e);
            throw e;
        }
    }

    private void sendWebSocketNotification(Long roomId, Long userId, LeaveTypeEnum leaveType, Long operatorId) {
        try {
            if (leaveType == LeaveTypeEnum.VOLUNTARY) {
                // 主动离开：广播 member_leave，通知房间内所有人
                wsProducer.sendEventToRoom(roomId, "member_leave", Map.of(
                        "userId", userId,
                        "roomId", roomId
                ));
            } else if (leaveType == LeaveTypeEnum.KICKED) {
                // 被踢：广播 member_kick + 向被踢用户私信 kicked
                wsProducer.sendEventToRoom(roomId, "member_kick", Map.of(
                        "targetUserId", userId,
                        "roomId", roomId
                ));
                wsProducer.sendEventToUser(userId, "kicked", Map.of(
                        "roomId", roomId,
                        "operatorId", operatorId
                ));
                log.info("[房间服务][removal] 用户{}已被踢出房间{}", userId, roomId);
            }
        } catch (Exception e) {
            log.warn("[房间服务][removal] WebSocket 通知失败 roomId={} userId={} leaveType={}",
                    roomId, userId, leaveType, e);
        }
    }

    private void checkAndCloseRoom(Long roomId) {
        try {
            Room room = roomMapper.selectById(roomId);
            if (room == null) {
                return;
            }
            // 仅在人数为 0 且房间仍为活跃时尝试关闭
            if (room.getCurrentMembers() != null && room.getCurrentMembers() == 0
                    && (room.getStatus() == null || room.getStatus() == RoomConst.STATUS_ACTIVE)) {
                room.setStatus(RoomConst.STATUS_CLOSED);
                room.setClosedTime(LocalDateTime.now());
                int updated = roomMapper.updateById(room);
                if (updated > 0) {
                    stringRedisTemplate.opsForHash().put(
                            RoomConst.metaKey(roomId), RoomConst.FIELD_STATUS, String.valueOf(RoomConst.STATUS_CLOSED));
                    log.info("[房间服务][removal] 房间{}因无成员自动关闭", roomId);
                }
            }
        } catch (Exception e) {
            log.warn("[房间服务][removal] 自动关房检查失败 roomId={}", roomId, e);
        }
    }
}
