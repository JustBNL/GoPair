package com.gopair.roomservice.messaging;

import com.gopair.common.constants.SystemConstants;
import com.gopair.roomservice.constant.RoomConst;
import com.gopair.roomservice.domain.event.LeaveRoomRequestedEvent;
import com.gopair.roomservice.domain.po.Room;
import com.gopair.roomservice.mapper.RoomMapper;
import com.gopair.roomservice.mapper.RoomMemberMapper;
import com.gopair.framework.logging.annotation.LogRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class LeaveRoomConsumer {

    private final RoomMapper roomMapper;
    private final RoomMemberMapper roomMemberMapper;
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 处理用户离开房间的 MQ 消费逻辑。
     *
     * * [核心策略]
     * - 幂等：仅在真实标记成员离开后才递减人数，重复消费不重复扣减。
     *
     * * [执行链路]
     * 1. 幂等标记离开：调用 markAsLeft，仅 leave_time IS NULL 时才更新。
     * 2. 递减人数：仅在真实更新后才执行。
     * 3. Redis 清理：members 集合移除、confirmed--。
     * 4. 自动关房：若房间人数为 0 且仍为活跃状态，标记为关闭并记录 closed_time。
     *
     * @param event 离开房间事件
     */
    @Transactional(rollbackFor = Exception.class)
    @RabbitListener(queues = SystemConstants.QUEUE_ROOM_LEAVE)
    @LogRecord(operation = "消费离开房间事件", module = "消息消费")
    public void handle(LeaveRoomRequestedEvent event) {
        Long roomId = event.getRoomId();
        Long userId = event.getUserId();
        try {
            // 幂等标记成员离开（leave_type=1 主动离开）
            LocalDateTime now = LocalDateTime.now();
            int updated = roomMemberMapper.markAsLeft(roomId, userId, now, 1);

            // 仅在真实更新成员后才递减人数，避免重复消费导致人数错误递减
            if (updated > 0) {
                int dec = roomMapper.decrementMembersIfPositive(roomId);
                if (dec == 1) {
                    try {
                        stringRedisTemplate.opsForHash().increment(RoomConst.metaKey(roomId), RoomConst.FIELD_CONFIRMED, -1);
                    } catch (Exception e) {
                        log.warn("[房间服务][leave] Redis confirmed-- 失败 roomId={} userId={} 错误={}", roomId, userId, e.getMessage());
                    }
                }
            }

            // Redis 成员集合移除
            try {
                stringRedisTemplate.opsForSet().remove(RoomConst.membersKey(roomId), String.valueOf(userId));
            } catch (Exception e) {
                log.warn("[房间服务][leave] Redis 移除成员失败 roomId={} userId={} 错误={}", roomId, userId, e.getMessage());
            }

            // 若房间无人，尝试关闭
            Room room = roomMapper.selectById(roomId);
            if (room != null && room.getCurrentMembers() != null && room.getCurrentMembers() == 0
                    && (room.getStatus() == null || room.getStatus() == RoomConst.STATUS_ACTIVE)) {
                room.setStatus(RoomConst.STATUS_CLOSED);
                room.setClosedTime(now);
                roomMapper.updateById(room);
                try {
                    stringRedisTemplate.opsForHash().put(RoomConst.metaKey(roomId), RoomConst.FIELD_STATUS,
                            String.valueOf(RoomConst.STATUS_CLOSED));
                } catch (Exception e) {
                    log.warn("[房间服务][leave] Redis 更新房间状态失败 roomId={} 错误={}", roomId, e.getMessage());
                }
                log.info("[房间服务][leave] 房间{}因无成员自动关闭", roomId);
            }
        } catch (Exception e) {
            log.error("[房间服务][leave] 消费异常 房间={} 用户={}", roomId, userId, e);
            throw e;
        }
    }
}
