package com.gopair.roomservice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gopair.common.core.PageResult;
import com.gopair.roomservice.domain.dto.RoomQueryDto;
import com.gopair.roomservice.domain.po.Room;
import com.gopair.roomservice.domain.po.RoomMember;
import com.gopair.roomservice.mapper.RoomMapper;
import com.gopair.roomservice.mapper.RoomMemberMapper;
import com.gopair.roomservice.mapper.UserPublicMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
    private ObjectMapper objectMapper;

    @InjectMocks
    private RoomMemberServiceImpl roomMemberService;

    @Test
    void testGetUserRooms_DefaultOnlyActiveRooms() {
        RoomQueryDto query = new RoomQueryDto();
        query.setPageNum(1);
        query.setPageSize(10);

        Page<RoomMember> memberPage = new Page<>(1, 10);
        RoomMember member = new RoomMember();
        member.setRoomId(100L);
        memberPage.setRecords(List.of(member));

        Room room = new Room();
        room.setRoomId(100L);
        room.setStatus(0);

        when(roomMemberMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(memberPage);
        when(roomMemberMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(member));
        when(roomMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(room));
        when(roomMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        PageResult<?> result = roomMemberService.getUserRooms(8L, query);

        assertEquals(1L, result.getTotal());

        ArgumentCaptor<LambdaQueryWrapper<Room>> listCaptor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        ArgumentCaptor<LambdaQueryWrapper<Room>> countCaptor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(roomMapper, times(1)).selectList(listCaptor.capture());
        verify(roomMapper, times(1)).selectCount(countCaptor.capture());

        String listSql = listCaptor.getValue().getSqlSegment();
        String countSql = countCaptor.getValue().getSqlSegment();
        assertTrue(listSql.contains("status"));
        assertTrue(countSql.contains("status"));
    }

    @Test
    void testGetUserRooms_IncludeHistoryShouldNotForceStatusFilter() {
        RoomQueryDto query = new RoomQueryDto();
        query.setPageNum(1);
        query.setPageSize(10);
        query.setIncludeHistory(true);

        Page<RoomMember> memberPage = new Page<>(1, 10);
        RoomMember member = new RoomMember();
        member.setRoomId(101L);
        memberPage.setRecords(List.of(member));

        Room room = new Room();
        room.setRoomId(101L);
        room.setStatus(1);

        when(roomMemberMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(memberPage);
        when(roomMemberMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(member));
        when(roomMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(room));
        when(roomMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        roomMemberService.getUserRooms(8L, query);

        ArgumentCaptor<LambdaQueryWrapper<Room>> listCaptor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        ArgumentCaptor<LambdaQueryWrapper<Room>> countCaptor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(roomMapper, times(1)).selectList(listCaptor.capture());
        verify(roomMapper, times(1)).selectCount(countCaptor.capture());

        String listSql = listCaptor.getValue().getSqlSegment();
        String countSql = countCaptor.getValue().getSqlSegment();
        assertFalse(listSql.contains("status"));
        assertFalse(countSql.contains("status"));
    }

    @Test
    void testGetUserRooms_StatusShouldOverrideIncludeHistory() {
        RoomQueryDto query = new RoomQueryDto();
        query.setPageNum(1);
        query.setPageSize(10);
        query.setIncludeHistory(true);
        query.setStatus(1);

        Page<RoomMember> memberPage = new Page<>(1, 10);
        RoomMember member = new RoomMember();
        member.setRoomId(102L);
        memberPage.setRecords(List.of(member));

        Room room = new Room();
        room.setRoomId(102L);
        room.setStatus(1);

        when(roomMemberMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(memberPage);
        when(roomMemberMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(member));
        when(roomMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(room));
        when(roomMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        roomMemberService.getUserRooms(8L, query);

        ArgumentCaptor<LambdaQueryWrapper<Room>> listCaptor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(roomMapper, times(1)).selectList(listCaptor.capture());

        String listSql = listCaptor.getValue().getSqlSegment();
        assertTrue(listSql.contains("status"));
    }
}
