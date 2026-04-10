package com.gopair.roomservice.messaging;

import com.gopair.common.constants.MessageConstants;
import com.gopair.framework.logging.annotation.LogRecord;
import com.gopair.roomservice.service.RoomMemberService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 消费用户离线事件，将该用户在所有房间的在线状态更新为离线。
 *
 * <h2>完整流程</h2>
 * <ol>
 *   <li><b>接收消息</b>：监听 user.offline.queue，消费 WebSocket 服务发送的离线事件。</li>
 *   <li><b>更新状态</b>：调用 roomMemberService.updateStatusToOffline，将用户在所有房间的 status 从 0 更新为 1。</li>
 *   <li><b>记录日志</b>：记录更新的成员记录数。</li>
 * </ol>
 *
 * <h2>幂等保证</h2>
 * updateStatusToOffline 使用 WHERE status = 0 条件，即使消息重复消费也不会产生重复更新。
 *
 * <h2>触发时机</h2>
 * 用户所有 WebSocket 连接断开时（断网、杀进程、超时等任何断开场景）触发。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserOfflineConsumer {

    private final RoomMemberService roomMemberService;

    @RabbitListener(queues = MessageConstants.QUEUE_USER_OFFLINE)
    @LogRecord(operation = "消费用户离线事件", module = "消息消费")
    public void handle(Object message) {
        try {
            Long userId = extractUserId(message);
            if (userId == null) {
                log.warn("[房间服务][user-offline] 无法从消息中提取 userId，跳过处理");
                return;
            }

            int updated = roomMemberService.updateStatusToOffline(userId);
            log.info("[房间服务][user-offline] 处理用户离线 userId={} updated={}", userId, updated);
        } catch (Exception e) {
            log.error("[房间服务][user-offline] 处理异常 message={}", message, e);
            throw e;
        }
    }

    /**
     * 从 MQ 消息中提取 userId。兼容直接发送的 Map 和通过 Jackson 序列化后反序列化得到的对象。
     */
    private Long extractUserId(Object message) {
        if (message instanceof Long) {
            return (Long) message;
        }
        if (message instanceof com.gopair.roomservice.domain.event.UserOfflineEvent event) {
            return event.getUserId();
        }
        if (message instanceof java.util.Map<?, ?> map) {
            Object userId = map.get("userId");
            if (userId != null) {
                return Long.valueOf(userId.toString());
            }
        }
        log.warn("[房间服务][user-offline] 无法识别的消息类型: {}", message.getClass().getName());
        return null;
    }
}
