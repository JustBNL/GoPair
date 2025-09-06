package com.gopair.roomservice.service;

import com.gopair.common.core.PageResult;
import com.gopair.common.entity.BaseQuery;
import com.gopair.roomservice.domain.vo.RoomVO;
import com.gopair.roomservice.mapper.RoomMapper;
import com.gopair.roomservice.mapper.RoomMemberMapper;
import com.gopair.roomservice.service.RoomMemberService;
import com.gopair.roomservice.service.impl.RoomServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 房间服务测试类
 */
@ExtendWith(MockitoExtension.class)
class RoomServiceTest {

    @Mock
    private RoomMapper roomMapper;

    @Mock
    private RoomMemberMapper roomMemberMapper;

    @Mock
    private RoomMemberService roomMemberService;

    @InjectMocks
    private RoomServiceImpl roomService;

    @Test
    void testGetUserRooms_ShouldReturnRoomsWithRelationshipInfo() {
        // 准备测试数据
        Long userId = 8L;
        BaseQuery query = new BaseQuery();
        query.setPageNum(1);
        query.setPageSize(10);

        // 模拟房间数据
        RoomVO room1 = new RoomVO();
        room1.setRoomId(1L);
        room1.setRoomName("测试房间1");
        room1.setOwnerId(userId); // 用户创建的房间
        room1.setCreateTime(LocalDateTime.now());

        RoomVO room2 = new RoomVO();
        room2.setRoomId(2L);
        room2.setRoomName("测试房间2");
        room2.setOwnerId(999L); // 用户加入的房间
        room2.setCreateTime(LocalDateTime.now());

        List<RoomVO> mockRooms = Arrays.asList(room1, room2);
        PageResult<RoomVO> mockResult = new PageResult<>(mockRooms, 2L, 1L, 10L);

        // 模拟RoomMemberService调用
        when(roomMemberService.getUserRooms(userId, query)).thenReturn(mockResult);

        // 执行测试
        PageResult<RoomVO> result = roomService.getUserRooms(userId, query);

        // 验证结果
        assertNotNull(result);
        assertEquals(2, result.getTotal());
        assertEquals(2, result.getRecords().size());

        // 验证调用
        verify(roomMemberService, times(1)).getUserRooms(userId, query);
    }

    @Test
    void testGetUserRooms_WithNullUserId_ShouldThrowException() {
        // 执行测试并验证异常
        BaseQuery query = new BaseQuery();
        
        assertThrows(Exception.class, () -> {
            roomService.getUserRooms(null, query);
        });
    }

    @Test
    void testGetUserRooms_EmptyResult_ShouldReturnEmptyPage() {
        // 准备测试数据
        Long userId = 8L;
        BaseQuery query = new BaseQuery();
        
        PageResult<RoomVO> emptyResult = new PageResult<>(List.of(), 0L, 1L, 10L);
        
        // 模拟空结果
        when(roomMemberService.getUserRooms(userId, query)).thenReturn(emptyResult);

        // 执行测试
        PageResult<RoomVO> result = roomService.getUserRooms(userId, query);

        // 验证结果
        assertNotNull(result);
        assertEquals(0, result.getTotal());
        assertTrue(result.getRecords().isEmpty());
    }
} 