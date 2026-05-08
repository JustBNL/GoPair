package com.gopair.adminservice.service;

import com.gopair.adminservice.base.BaseIntegrationTest;
import com.gopair.adminservice.context.AdminContext;
import com.gopair.adminservice.context.AdminContextHolder;
import com.gopair.adminservice.domain.po.Room;
import com.gopair.adminservice.mapper.RoomMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 管理员禁用/解禁房间功能集成测试。
 *
 * * [核心策略]
 * - 禁用时：更新 DB status=4 + disabled_time + disabled_reason，通过 MQ 通知 room-service。
 * - 解禁时：更新 DB status=0，清空 disabled 字段，通过 MQ 通知 room-service。
 * - DB 变更在事务中执行，MQ 发布在 afterCommit 回调中。
 * - 验证：禁用后 status=4，禁用字段非空；解禁后 status=0，禁用字段清空。
 */
class RoomDisableEnableIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private RoomManageService roomManageService;

    @Autowired
    private RoomMapper roomMapper;

    @BeforeEach
    void setUp() {
        AdminContextHolder.set(new AdminContext(1L, "testadmin"));
    }

    @AfterEach
    void tearDown() {
        AdminContextHolder.clear();
    }

    @Test
    @DisplayName("禁用房间：status 改为 4，disabled_time 和 disabled_reason 被记录")
    void disableRoom_setsStatusAndFields() {
        Room room = createTestRoom(0);

        roomManageService.disableRoom(room.getRoomId(), "违规内容");

        Room updated = roomMapper.selectById(room.getRoomId());
        assertEquals(4, updated.getStatus());
        assertNotNull(updated.getDisabledTime());
        assertEquals("违规内容", updated.getDisabledReason());
    }

    @Test
    @DisplayName("解禁房间：status 恢复为 0，disabled_time 和 disabled_reason 清空")
    void enableRoom_clearsDisabledFields() {
        Room room = createTestRoom(4);
        room.setDisabledTime(LocalDateTime.now());
        room.setDisabledReason("违规");
        roomMapper.updateById(room);

        roomManageService.enableRoom(room.getRoomId());

        Room updated = roomMapper.selectById(room.getRoomId());
        assertEquals(0, updated.getStatus());
        assertNull(updated.getDisabledTime());
        assertNull(updated.getDisabledReason());
    }

    @Test
    @DisplayName("禁用不存在的房间：抛出 IllegalArgumentException")
    void disableRoom_notFound() {
        assertThrows(IllegalArgumentException.class, () ->
            roomManageService.disableRoom(999999L, "reason"));
    }

    @Test
    @DisplayName("解禁不存在的房间：抛出 IllegalArgumentException")
    void enableRoom_notFound() {
        assertThrows(IllegalArgumentException.class, () ->
            roomManageService.enableRoom(999999L));
    }

    private Room createTestRoom(int status) {
        Room room = new Room();
        room.setRoomName("测试房间");
        room.setRoomCode("TEST" + System.currentTimeMillis() % 100000);
        room.setOwnerId(1L);
        room.setMaxMembers(10);
        room.setCurrentMembers(1);
        room.setStatus(status);
        room.setExpireTime(LocalDateTime.now().plusHours(24));
        roomMapper.insert(room);
        return room;
    }
}
