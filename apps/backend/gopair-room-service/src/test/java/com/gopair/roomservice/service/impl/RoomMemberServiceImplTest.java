package com.gopair.roomservice.service.impl;

import com.gopair.roomservice.domain.po.Room;
import com.gopair.roomservice.domain.po.RoomMember;
import com.gopair.roomservice.mapper.RoomMapper;
import com.gopair.roomservice.mapper.RoomMemberMapper;
import com.gopair.roomservice.mapper.UserPublicMapper;
import com.gopair.roomservice.service.impl.RoomMemberServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 房间成员服务单元测试
 *
 * * [核心策略]
 * - 测试无状态方法：isMemberInRoom、addMember、removeMember 等方法直接操作 Mapper，无 MyBatis-Plus Lambda 依赖。
 * - 模拟存储：使用 mock 存储模拟数据库操作，通过 doAnswer 捕获 insert/update/delete 调用。
 *
 * * [测试范围]
 * - isMemberInRoom：成员存在/不存在判断
 * - addMember：新增成员成功/重复新增
 * - removeMember：移除成员成功/成员不存在
 * - updateLastActiveTime：更新最后活跃时间
 */
@ExtendWith(MockitoExtension.class)
class RoomMemberServiceImplTest {

    @Mock
    private RoomMemberMapper roomMemberMapper;

    @Mock
    private RoomMapper roomMapper;

    @Mock
    private UserPublicMapper userPublicMapper;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    private RoomMemberServiceImpl roomMemberService;

    @BeforeEach
    void setUp() {
        roomMemberService = new RoomMemberServiceImpl(
            roomMemberMapper, roomMapper, userPublicMapper, restTemplate, objectMapper
        );
    }

    @Test
    void testIsMemberInRoom_WhenMemberExists_ShouldReturnTrue() {
        Long roomId = 1L;
        Long userId = 100L;

        RoomMember existingMember = new RoomMember();
        existingMember.setRoomId(roomId);
        existingMember.setUserId(userId);

        when(roomMemberMapper.selectOne(any())).thenReturn(existingMember);

        boolean result = roomMemberService.isMemberInRoom(roomId, userId);

        assertTrue(result);
        verify(roomMemberMapper).selectOne(any());
    }

    @Test
    void testIsMemberInRoom_WhenMemberDoesNotExist_ShouldReturnFalse() {
        Long roomId = 1L;
        Long userId = 100L;

        when(roomMemberMapper.selectOne(any())).thenReturn(null);

        boolean result = roomMemberService.isMemberInRoom(roomId, userId);

        assertFalse(result);
        verify(roomMemberMapper).selectOne(any());
    }

    @Test
    void testAddMember_WhenNotInRoom_ShouldAddSuccessfully() {
        Long roomId = 1L;
        Long userId = 100L;

        when(roomMemberMapper.selectOne(any())).thenReturn(null);
        when(roomMemberMapper.insert(any(RoomMember.class))).thenReturn(1);

        boolean result = roomMemberService.addMember(roomId, userId, null);

        assertTrue(result);
        verify(roomMemberMapper).selectOne(any());
        verify(roomMemberMapper).insert(any(RoomMember.class));
    }

    @Test
    void testAddMember_WhenAlreadyInRoom_ShouldReturnFalse() {
        Long roomId = 1L;
        Long userId = 100L;

        RoomMember existingMember = new RoomMember();
        existingMember.setRoomId(roomId);
        existingMember.setUserId(userId);

        when(roomMemberMapper.selectOne(any())).thenReturn(existingMember);

        boolean result = roomMemberService.addMember(roomId, userId, null);

        assertFalse(result);
    }

    @Test
    void testRemoveMember_WhenMemberExists_ShouldRemoveSuccessfully() {
        Long roomId = 1L;
        Long userId = 100L;

        when(roomMemberMapper.delete(any())).thenReturn(1);

        boolean result = roomMemberService.removeMember(roomId, userId);

        assertTrue(result);
        verify(roomMemberMapper).delete(any());
    }

    @Test
    void testRemoveMember_WhenMemberDoesNotExist_ShouldReturnFalse() {
        Long roomId = 1L;
        Long userId = 100L;

        when(roomMemberMapper.delete(any())).thenReturn(0);

        boolean result = roomMemberService.removeMember(roomId, userId);

        assertFalse(result);
        verify(roomMemberMapper).delete(any());
    }

    @Test
    void testUpdateLastActiveTime_WhenMemberExists_ShouldUpdateSuccessfully() {
        Long roomId = 1L;
        Long userId = 100L;

        when(roomMemberMapper.update(any(RoomMember.class), any())).thenReturn(1);

        boolean result = roomMemberService.updateLastActiveTime(roomId, userId);

        assertTrue(result);
        verify(roomMemberMapper).update(any(RoomMember.class), any());
    }

    @Test
    void testUpdateLastActiveTime_WhenMemberDoesNotExist_ShouldReturnFalse() {
        Long roomId = 1L;
        Long userId = 100L;

        when(roomMemberMapper.update(any(RoomMember.class), any())).thenReturn(0);

        boolean result = roomMemberService.updateLastActiveTime(roomId, userId);

        assertFalse(result);
        verify(roomMemberMapper).update(any(RoomMember.class), any());
    }

    @Test
    void testDeleteByRoomId_WhenMembersExist_ShouldDeleteSuccessfully() {
        Long roomId = 1L;

        when(roomMemberMapper.delete(any())).thenReturn(5);

        boolean result = roomMemberService.deleteByRoomId(roomId);

        assertTrue(result);
        verify(roomMemberMapper).delete(any());
    }

    @Test
    void testDeleteByRoomId_WhenNoMembers_ShouldReturnFalse() {
        Long roomId = 1L;

        when(roomMemberMapper.delete(any())).thenReturn(0);

        boolean result = roomMemberService.deleteByRoomId(roomId);

        assertFalse(result);
        verify(roomMemberMapper).delete(any());
    }
}
