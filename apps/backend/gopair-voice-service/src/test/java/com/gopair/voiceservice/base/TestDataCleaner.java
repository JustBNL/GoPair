package com.gopair.voiceservice.base;

import com.gopair.voiceservice.mapper.VoiceCallMapper;
import com.gopair.voiceservice.mapper.VoiceCallParticipantMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 测试数据库清理工具。
 * 提供手动清理测试数据的方法，替代 @Transactional 回滚。
 *
 * * [核心策略]
 * - 按房间 ID 精确清理：只删除当前测试创建的 voice_call 和 voice_call_participant 记录。
 * - 依赖 JdbcTemplate 直接执行 DELETE，不经过 MyBatis 事务。
 * - 每个测试类在 @BeforeEach 中调用 cleanup() 清理脏数据。
 *
 * @author gopair
 */
@Slf4j
@Component
public class TestDataCleaner {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired(required = false)
    private VoiceCallParticipantMapper participantMapper;

    @Autowired(required = false)
    private VoiceCallMapper voiceCallMapper;

    /**
     * 按房间 ID 清理所有相关测试数据。
     * 删除顺序：先删 participant，再删 call（避免外键问题）。
     */
    public void cleanupByRoomId(Long roomId) {
        if (roomId == null) return;
        jdbcTemplate.update("DELETE FROM voice_call_participant WHERE call_id IN (SELECT call_id FROM voice_call WHERE room_id = ?)", roomId);
        jdbcTemplate.update("DELETE FROM voice_call WHERE room_id = ?", roomId);
    }

    /**
     * 按通话 ID 清理所有相关测试数据。
     */
    public void cleanupByCallId(Long callId) {
        if (callId == null) return;
        jdbcTemplate.update("DELETE FROM voice_call_participant WHERE call_id = ?", callId);
        jdbcTemplate.update("DELETE FROM voice_call WHERE call_id = ?", callId);
    }

    /**
     * 清理所有测试数据（全量清空，慎用）。
     */
    public void cleanupAll() {
        int p = jdbcTemplate.update("DELETE FROM voice_call_participant");
        int c = jdbcTemplate.update("DELETE FROM voice_call");
        log.info("[TestDataCleaner] cleanupAll: deleted {} participants, {} calls", p, c);
    }
}
