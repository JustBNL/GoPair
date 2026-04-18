package com.gopair.voiceservice.base;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 录音式 WebSocket 消息推送记录器。
 *
 * * [核心策略]
 * - 继承 WebSocketMessageProducer，作为 primary bean 注入 VoiceCallServiceImpl。
 * - 记录所有 sendEventToRoom / sendSignalingMessage 调用，支持测试断言。
 * - 不依赖 Mockito，手动清空列表即可重置，规避 Java 23 + Byte Buddy 不兼容问题。
 *
 * @author gopair
 */
public class RecordingWebSocketProducer extends com.gopair.common.service.WebSocketMessageProducer {

    private final List<Event> events = new CopyOnWriteArrayList<>();
    private final List<Signaling> signalings = new CopyOnWriteArrayList<>();

    public RecordingWebSocketProducer() {
        super(null);
    }

    // ==================== 推送方法（由 VoiceCallService 调用） ====================

    @Override
    public void sendEventToRoom(Long roomId, String eventType, Map<String, Object> payload) {
        events.add(new Event(roomId, eventType, payload));
    }

    @Override
    public void sendSignalingMessage(Long userId, Map<String, Object> payload) {
        signalings.add(new Signaling(userId, payload));
    }

    // ==================== 查询方法（供测试断言） ====================

    public List<Event> getEvents() {
        return Collections.unmodifiableList(events);
    }

    public List<Signaling> getSignalings() {
        return Collections.unmodifiableList(signalings);
    }

    /**
     * 重置所有记录。不依赖 Mockito，规避 Byte Buddy 问题。
     */
    public void reset() {
        events.clear();
        signalings.clear();
    }

    // ==================== 断言辅助方法 ====================

    public int countRoomEvents(Long roomId, String eventType) {
        return (int) events.stream()
                .filter(e -> Objects.equals(e.roomId, roomId) && Objects.equals(e.eventType, eventType))
                .count();
    }

    public int countSignalingTo(Long userId) {
        return (int) signalings.stream()
                .filter(s -> Objects.equals(s.userId, userId))
                .count();
    }

    public boolean hasEvent(Long roomId, String eventType) {
        return events.stream().anyMatch(e ->
                Objects.equals(e.roomId, roomId) && Objects.equals(e.eventType, eventType));
    }

    public boolean hasSignalingTo(Long userId) {
        return signalings.stream().anyMatch(s -> Objects.equals(s.userId, userId));
    }

    // ==================== 数据结构 ====================

    public record Event(Long roomId, String eventType, Map<String, Object> payload) {}

    public record Signaling(Long userId, Map<String, Object> payload) {}
}
