package com.gopair.roomservice.messaging;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gopair.common.constants.SystemConstants;
import com.gopair.roomservice.constant.RoomConst;
import com.gopair.roomservice.domain.event.JoinRoomRequestedEvent;
import com.gopair.roomservice.domain.po.Room;
import com.gopair.roomservice.domain.po.RoomMember;
import com.gopair.roomservice.mapper.RoomMapper;
import com.gopair.roomservice.mapper.RoomMemberMapper;
import com.gopair.framework.logging.annotation.LogRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class JoinRoomConsumer {

    private final RoomMapper roomMapper;
    private final RoomMemberMapper roomMemberMapper;
    private final StringRedisTemplate stringRedisTemplate;

    @Value("${gopair.room.reservation.join-result-ttl-seconds:300}")
    private long joinResultTtlSeconds;

    /**
     * 处理用户异步加入房间的 MQ 消费逻辑。
     *
     * <h2>完整流程</h2>
     * <ol>
     *   <li><b>活跃成员判断</b>：查询 room_member，若 leave_time IS NULL 则为活跃成员，直接幂等返回。</li>
     *   <li><b>历史记录复用</b>：若有 leave_time 非空的历史记录，复用该记录并重置状态。</li>
     *   <li><b>插入新记录</b>：无历史记录时写入新记录（唯一键兜底，可能因并发重试而抛异常）。</li>
     *   <li><b>原子加一</b>：若房间状态为 ACTIVE，执行 UPDATE current_members + 1（乐观锁，不满才加）。</li>
     *     <ul>
     *       <li>加成功：incOk=true，Redis confirmed++</li>
     *       <li>加失败（已满/唯一键冲突）：incOk=false，进入补偿分支</li>
     *     </ul>
     *   <li><b>释放预占</b>：调用 {@link #releasePendingReservation(Long, Long)}，
     *     只有 pending 中仍存有该用户时才扣减 reserved 并删 pending，防止消息重试时重复扣减。</li>
     *   <li><b>结果分支</b>（在释放预占之后）：
     *     <ul>
     *       <li>活跃成员 → token=JOINED，members.add，return</li>
     *       <li>inserted && incOk → token=JOINED，members.add</li>
     *       <li>其余情况 → 删除 DB 记录（仅删 leave_time IS NULL 的），token=FAILED</li>
     *     </ul>
     *   </li>
     * </ol>
     *
     * <h2>消息重试场景下的幂等保证</h2>
     * pending entry 是本次处理的"令牌"。只有持有令牌的调用者才执行 reserved--，
     * 消息重试时 pending 已被上一轮删掉，重试方跳过 reserved 扣减，不影响 reserved 计数。
     *
     * @param event 加入房间事件，包含 roomId、userId、joinToken 等
     */
    @Transactional(rollbackFor = Exception.class)
    @RabbitListener(queues = SystemConstants.QUEUE_ROOM_JOIN)
    @LogRecord(operation = "消费加入房间事件", module = "消息消费")
    public void handle(JoinRoomRequestedEvent event) {
        Long roomId = event.getRoomId();
        Long userId = event.getUserId();
        String token = event.getJoinToken();
        try {
            boolean inserted = false;
            boolean incOk = false;
            boolean fromHistory = false;

            // 幂等处理：分三种情况
            // 1. 活跃成员（leave_time IS NULL）→ 幂等成功，直接返回
            // 2. 历史记录（leave_time 非空）→ 复用该记录，更新状态后再执行原子加一
            // 3. 无记录 → 插入新记录，执行原子加一
            LambdaQueryWrapper<RoomMember> activeQuery = new LambdaQueryWrapper<>();
            activeQuery.eq(RoomMember::getRoomId, roomId).eq(RoomMember::getUserId, userId)
                    .isNull(RoomMember::getLeaveTime);
            RoomMember activeMember = roomMemberMapper.selectOne(activeQuery);
            if (activeMember != null) {
                // 已在房间，幂等成功
                stringRedisTemplate.opsForSet().add(RoomConst.membersKey(roomId), String.valueOf(userId));
                stringRedisTemplate.opsForValue().set(RoomConst.joinTokenKey(token),
                        roomId + ":" + userId + ":" + RoomConst.JOIN_RESULT_JOINED, joinResultTtlSeconds, TimeUnit.SECONDS);
                releasePendingReservation(roomId, userId);
                if (log.isDebugEnabled()) {
                    log.debug("[房间服务][join-async] 活跃成员幂等 房间={} 用户={} token={}", roomId, userId, token);
                }
                return;
            }

            // 非活跃成员，尝试查找历史记录
            LambdaQueryWrapper<RoomMember> historyQuery = new LambdaQueryWrapper<>();
            historyQuery.eq(RoomMember::getRoomId, roomId).eq(RoomMember::getUserId, userId)
                    .isNotNull(RoomMember::getLeaveTime);
            RoomMember history = roomMemberMapper.selectOne(historyQuery);
            if (history != null) {
                // 复用历史记录：使用显式 SQL 清空 leave_time/leave_type，绕过 MyBatis-Plus null 字段忽略策略
                int updated = roomMemberMapper.reactivateMember(
                        roomId, userId,
                        RoomConst.MEMBER_STATUS_ONLINE,
                        java.time.LocalDateTime.now(),
                        java.time.LocalDateTime.now());
                inserted = updated == 1;
                fromHistory = updated == 1;
            } else {
                // 插入新成员（唯一键兜底，可能因并发重试而抛异常）
                RoomMember member = new RoomMember();
                member.setRoomId(roomId);
                member.setUserId(userId);
                member.setRole(RoomConst.ROLE_MEMBER);
                member.setStatus(RoomConst.MEMBER_STATUS_ONLINE);
                member.setJoinTime(java.time.LocalDateTime.now());
                member.setLastActiveTime(java.time.LocalDateTime.now());
                try {
                    roomMemberMapper.insert(member);
                    inserted = true;
                } catch (Exception dup) {
                    inserted = false; // 唯一键冲突视为已存在，跳过原子加一
                }
            }

            // 原子加一（仅在未满时）
            Room room = roomMapper.selectById(roomId);
            if (room != null && room.getStatus() != null && room.getStatus() == RoomConst.STATUS_ACTIVE) {
                int updated = roomMapper.incrementMembersIfNotFull(roomId);
                if (updated == 1) {
                    incOk = true;
                    try { stringRedisTemplate.opsForHash().increment(RoomConst.metaKey(roomId), RoomConst.FIELD_CONFIRMED, 1); } catch (Exception ignore) {}
                } else {
                    log.warn("[房间服务][join-async] 房间{}成员计数未生效，token={}", roomId, token);
                }
            }

            releasePendingReservation(roomId, userId);

            if (inserted && incOk) {
                // 数据库插入成功且成员计数 +1，判定加入成功
                stringRedisTemplate.opsForSet().add(RoomConst.membersKey(roomId), String.valueOf(userId));
                stringRedisTemplate.opsForValue().set(RoomConst.joinTokenKey(token),
                        roomId + ":" + userId + ":" + RoomConst.JOIN_RESULT_JOINED, joinResultTtlSeconds, TimeUnit.SECONDS);
                log.info("[房间服务][join-async] 消费成功 房间={} 用户={} token={}", roomId, userId, token);
                if (log.isDebugEnabled()) {
                    log.debug("[房间服务][join-async] 消费成功后状态 房间={} metaReserved={} pendingMembers={} memberCount={}",
                            roomId,
                            stringRedisTemplate.opsForHash().get(RoomConst.metaKey(roomId), RoomConst.FIELD_RESERVED),
                            stringRedisTemplate.opsForHash().keys(RoomConst.pendingKey(roomId)),
                            stringRedisTemplate.opsForSet().size(RoomConst.membersKey(roomId)));
                }
            } else {
                // 补偿删除：仅在本次 insert 分支插入记录时执行（fromHistory=false）
                // fromHistory=true 时，reactivateMember 已更新历史记录 leave_time 已清空，无需删除
                if (!fromHistory) {
                    try {
                        LambdaQueryWrapper<RoomMember> del = new LambdaQueryWrapper<>();
                        del.eq(RoomMember::getRoomId, roomId)
                           .eq(RoomMember::getUserId, userId);
                        roomMemberMapper.delete(del);
                    } catch (Exception ignore) {}
                }
                stringRedisTemplate.opsForValue().set(RoomConst.joinTokenKey(token),
                        roomId + ":" + userId + ":" + RoomConst.JOIN_RESULT_FAILED, joinResultTtlSeconds, TimeUnit.SECONDS);
                log.warn("[房间服务][join-async] 消费补偿 房间={} 用户={} token={} inserted={} incOk={}", roomId, userId, token, inserted, incOk);
                if (log.isDebugEnabled()) {
                    log.debug("[房间服务][join-async] 补偿后状态 房间={} metaReserved={} pendingMembers={} memberCount={}",
                            roomId,
                            stringRedisTemplate.opsForHash().get(RoomConst.metaKey(roomId), RoomConst.FIELD_RESERVED),
                            stringRedisTemplate.opsForHash().keys(RoomConst.pendingKey(roomId)),
                            stringRedisTemplate.opsForSet().size(RoomConst.membersKey(roomId)));
                }
            }
        } catch (Exception e) {
            //异常也回滚预占数据（同样遵循：只有 pending 仍存在时才扣 reserved）
            log.error("[房间服务][join-async] 消费异常 房间={} 用户={} token={}", roomId, userId, token, e);
            try {
                releasePendingReservation(roomId, userId);
                stringRedisTemplate.opsForValue().set(RoomConst.joinTokenKey(token),
                        roomId + ":" + userId + ":" + RoomConst.JOIN_RESULT_FAILED, joinResultTtlSeconds, TimeUnit.SECONDS);
                if (log.isDebugEnabled()) {
                    log.debug("[房间服务][join-async] 异常恢复 房间={} pendingMembers={} reserved={} memberCount={}",
                            roomId,
                            stringRedisTemplate.opsForHash().keys(RoomConst.pendingKey(roomId)),
                            stringRedisTemplate.opsForHash().get(RoomConst.metaKey(roomId), RoomConst.FIELD_RESERVED),
                            stringRedisTemplate.opsForSet().size(RoomConst.membersKey(roomId)));
                }
            } catch (Exception ignore) {}
            throw e;
        }
    }

    /**
     * 释放用户的预占状态：若 pending 中仍存在该用户则扣减 reserved 并删除 pending entry。
     * 若 pending 已被删除（消息重试场景中前一轮已处理过），则跳过以避免 reserved 被重复扣减。
     * reserved 变为负数时自动纠正并打 error 日志（理论上不应发生，出现说明上游有未知 bug）。
     *
     * @param roomId 房间 ID
     * @param userId 用户 ID
     */
    private void releasePendingReservation(Long roomId, Long userId) {
        Boolean pendingExists = stringRedisTemplate.opsForHash()
                .hasKey(RoomConst.pendingKey(roomId), String.valueOf(userId));
        if (!Boolean.TRUE.equals(pendingExists)) {
            return;
        }
        Long reservedAfter = stringRedisTemplate.opsForHash()
                .increment(RoomConst.metaKey(roomId), RoomConst.FIELD_RESERVED, -1);
        if (reservedAfter != null && reservedAfter < 0) {
            stringRedisTemplate.opsForHash().put(
                    RoomConst.metaKey(roomId), RoomConst.FIELD_RESERVED, "0");
            log.error("[房间服务][join-async] reserved 异常为负，已自动纠正 roomId={} userId={} 原值={}",
                    roomId, userId, reservedAfter);
        }
        stringRedisTemplate.opsForHash().delete(RoomConst.pendingKey(roomId), String.valueOf(userId));
    }
}
