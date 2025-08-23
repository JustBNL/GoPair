package com.gopair.roomservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gopair.roomservice.domain.po.RoomMember;
import org.apache.ibatis.annotations.Param;

/**
 * 房间成员数据访问接口
 * 
 * @author gopair
 */
public interface RoomMemberMapper extends BaseMapper<RoomMember> {

    /**
     * 统计房间当前成员数量
     * 
     * @param roomId 房间ID
     * @return 成员数量
     */
    int countMembersByRoomId(@Param("roomId") Long roomId);
} 