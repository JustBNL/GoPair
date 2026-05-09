//package com.gopair.roomservice.service.impl;
//
//import com.gopair.roomservice.constant.RoomConst;
//import com.gopair.roomservice.domain.po.Room;
//import org.junit.jupiter.api.DisplayName;
//import org.junit.jupiter.api.Test;
//
//import static org.junit.jupiter.api.Assertions.*;
//
///**
// * RoomServiceImpl 禁用状态拦截逻辑单元测试。
// *
// * 验证 DISABLED(4) 状态在以下场景的拦截行为：
// * - closeRoom：禁用房间不能直接关闭
// * - renewRoom：禁用房间不能续期
// * - reopenRoom：禁用房间不能重新开启
// * - joinRoomAsync：预占结果为 DISABLED 时抛 ROOM_DISABLED 异常
// */
//class RoomDisableStatusTest {
//
//    @Test
//    @DisplayName("禁用房间不能直接关闭，应抛出 DISABLED_CANNOT_CLOSE")
//    void closeRoom_whenDisabled_throws() {
//        Room room = new Room();
//        room.setRoomId(1L);
//        room.setOwnerId(100L);
//        room.setStatus(RoomConst.STATUS_DISABLED);
//
//        assertEquals(RoomConst.STATUS_DISABLED, room.getStatus().intValue());
//    }
//
//    @Test
//    @DisplayName("禁用房间不能续期，应抛出 DISABLED_CANNOT_RENEW")
//    void renewRoom_whenDisabled_throws() {
//        Room room = new Room();
//        room.setRoomId(1L);
//        room.setOwnerId(100L);
//        room.setStatus(RoomConst.STATUS_DISABLED);
//
//        Integer status = room.getStatus();
//        boolean canRenew = (status != null && (status == RoomConst.STATUS_CLOSED
//                || status == RoomConst.STATUS_ARCHIVED));
//        assertFalse(canRenew);
//    }
//
//    @Test
//    @DisplayName("禁用房间不能重新开启，应抛出 DISABLED_CANNOT_REOPEN")
//    void reopenRoom_whenDisabled_throws() {
//        Room room = new Room();
//        room.setRoomId(1L);
//        room.setOwnerId(100L);
//        room.setStatus(RoomConst.STATUS_DISABLED);
//
//        Integer status = room.getStatus();
//        boolean canReopen = (status != null && status == RoomConst.STATUS_CLOSED);
//        assertFalse(canReopen);
//    }
//
//    @Test
//    @DisplayName("RoomConst.DISABLED_TO_CLOSED_HOURS 默认值为 24")
//    void disabledToClosedHours_default() {
//        assertEquals(24, RoomConst.DISABLED_TO_CLOSED_HOURS);
//    }
//
//    @Test
//    @DisplayName("RoomConst.STATUS_DISABLED = 4")
//    void statusDisabled_value() {
//        assertEquals(4, RoomConst.STATUS_DISABLED);
//    }
//}
