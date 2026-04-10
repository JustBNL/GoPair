package com.gopair.roomservice.service;

import com.gopair.common.core.PageResult;
import com.gopair.roomservice.domain.dto.RoomQueryDto;
import com.gopair.roomservice.domain.po.Room;
import com.gopair.roomservice.domain.po.RoomMember;
import com.gopair.roomservice.domain.vo.RoomVO;
import com.gopair.roomservice.mapper.RoomMapper;
import com.gopair.roomservice.mapper.RoomMemberMapper;
import com.gopair.roomservice.service.impl.RoomServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 房间服务单元测试
 */
@ExtendWith(MockitoExtension.class)
class RoomServiceTest {

    @Mock
    private RoomMapper roomMapper;

    @Mock
    private RoomMemberMapper roomMemberMapper;

    @Mock
    private RoomMemberService roomMemberService;

    @Mock
    private com.gopair.roomservice.service.JoinReservationService joinReservationService;

    @Mock
    private com.gopair.roomservice.service.JoinResultQueryService joinResultQueryService;

    @Mock
    private com.gopair.roomservice.service.RoomCacheSyncService roomCacheSyncService;

    @Mock
    private com.gopair.roomservice.messaging.LeaveRoomProducer leaveRoomProducer;

    @Mock
    private org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;

    @Mock
    private com.gopair.common.service.WebSocketMessageProducer wsProducer;

    @Mock
    private com.gopair.roomservice.config.RoomConfig roomConfig;

    private RoomServiceImpl roomService;

    @BeforeEach
    void setUp() {
        roomService = new RoomServiceImpl(
            roomMapper,
            roomMemberMapper,
            roomMemberService,
            joinReservationService,
            joinResultQueryService,
            roomCacheSyncService,
            leaveRoomProducer,
            stringRedisTemplate,
            wsProducer,
            roomConfig
        );
    }

    @Test
    void testGetUserRooms_ShouldReturnRoomsWithRelationshipInfo() {
        Long userId = 8L;
        RoomQueryDto query = new RoomQueryDto();
        query.setPageNum(1);
        query.setPageSize(10);

        RoomVO room1 = new RoomVO();
        room1.setRoomId(1L);
        room1.setRoomName("测试房间1");
        room1.setOwnerId(userId);
        room1.setCreateTime(LocalDateTime.now());

        RoomVO room2 = new RoomVO();
        room2.setRoomId(2L);
        room2.setRoomName("测试房间2");
        room2.setOwnerId(999L);
        room2.setCreateTime(LocalDateTime.now());

        List<RoomVO> mockRooms = List.of(room1, room2);
        PageResult<RoomVO> mockResult = new PageResult<>(mockRooms, 2L, 1L, 10L);

        when(roomMemberService.getUserRooms(eq(userId), any(RoomQueryDto.class))).thenReturn(mockResult);

        PageResult<RoomVO> result = roomService.getUserRooms(userId, query);

        assertNotNull(result);
        assertEquals(2, result.getTotal());
        assertEquals(2, result.getRecords().size());
        verify(roomMemberService, times(1)).getUserRooms(eq(userId), any(RoomQueryDto.class));
    }

    @Test
    void testGetUserRooms_EmptyResult_ShouldReturnEmptyPage() {
        Long userId = 8L;
        RoomQueryDto query = new RoomQueryDto();

        PageResult<RoomVO> emptyResult = new PageResult<>(List.of(), 0L, 1L, 10L);

        when(roomMemberService.getUserRooms(eq(userId), any(RoomQueryDto.class))).thenReturn(emptyResult);

        PageResult<RoomVO> result = roomService.getUserRooms(userId, query);

        assertNotNull(result);
        assertEquals(0, result.getTotal());
        assertTrue(result.getRecords().isEmpty());
    }

    @Test
    void testIsMemberInRoom_DelegatesToRoomMemberService() {
        Long roomId = 1L;
        Long userId = 100L;

        when(roomMemberService.isMemberInRoom(roomId, userId)).thenReturn(true);

        assertTrue(roomService.isMemberInRoom(roomId, userId));
        verify(roomMemberService).isMemberInRoom(roomId, userId);
    }

    @Test
    void testIsMemberInRoom_WithNullRoomId_ShouldReturnFalse() {
        assertFalse(roomService.isMemberInRoom(null, 100L));
    }

    @Test
    void testIsMemberInRoom_WithNullUserId_ShouldReturnFalse() {
        assertFalse(roomService.isMemberInRoom(1L, null));
    }

    @Test
    void testGetRoomByCode_WithValidCode_ShouldReturnRoom() {
        String roomCode = "12345678";
        Room room = new Room();
        room.setRoomId(1L);
        room.setRoomName("测试房间");
        room.setRoomCode(roomCode);
        room.setCreateTime(LocalDateTime.now());

        when(roomMapper.selectByRoomCode(roomCode)).thenReturn(room);

        RoomVO result = roomService.getRoomByCode(roomCode);

        assertNotNull(result);
        assertEquals(1L, result.getRoomId());
        assertEquals("测试房间", result.getRoomName());
    }

    @Test
    void testFindExpiredRooms_ShouldReturnExpiredRooms() {
        Room expiredRoom = new Room();
        expiredRoom.setRoomId(1L);
        expiredRoom.setRoomName("过期房间");

        when(roomMapper.selectExpiredRooms(any(LocalDateTime.class))).thenReturn(List.of(expiredRoom));

        List<Room> result = roomService.findExpiredRooms();

        assertNotNull(result);
        assertEquals(1, result.size());
        verify(roomMapper).selectExpiredRooms(any(LocalDateTime.class));
    }

    @Test
    void testIsRoomCodeUnique_WhenCodeExists_ShouldReturnFalse() {
        String roomCode = "12345678";
        Room existingRoom = new Room();
        existingRoom.setRoomId(1L);

        when(roomMapper.selectByRoomCode(roomCode)).thenReturn(existingRoom);

        assertFalse(roomService.isRoomCodeUnique(roomCode));
    }

    @Test
    void testIsRoomCodeUnique_WhenCodeNotExists_ShouldReturnTrue() {
        String roomCode = "XYZ99999";

        when(roomMapper.selectByRoomCode(roomCode)).thenReturn(null);

        assertTrue(roomService.isRoomCodeUnique(roomCode));
    }
}
