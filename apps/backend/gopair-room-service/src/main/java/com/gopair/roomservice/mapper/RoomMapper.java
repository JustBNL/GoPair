package com.gopair.roomservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gopair.roomservice.domain.po.Room;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 房间数据访问接口
 * 
 * @author gopair
 */
public interface RoomMapper extends BaseMapper<Room> {

    /**
     * 根据房间码查询房间
     * 
     * @param roomCode 房间码
     * @return 房间信息
     */
    Room selectByRoomCode(@Param("roomCode") String roomCode);

    /**
     * 查询过期房间列表
     * 
     * @param currentTime 当前时间
     * @return 过期房间列表
     */
    List<Room> selectExpiredRooms(@Param("currentTime") LocalDateTime currentTime);

    /**
     * 根据用户ID查询用户创建的房间列表
     * 
     * @param ownerId 房主ID
     * @return 房间列表
     */
    List<Room> selectRoomsByOwnerId(@Param("ownerId") Long ownerId);

    /**
     * 更新房间当前成员数
     * 
     * @param roomId 房间ID
     * @param currentMembers 当前成员数
     * @param version 乐观锁版本
     * @return 更新行数
     */
    int updateCurrentMembers(@Param("roomId") Long roomId, 
                           @Param("currentMembers") Integer currentMembers,
                           @Param("version") Integer version);

    // 原子加一（仅在未满时）
    int incrementMembersIfNotFull(@Param("roomId") Long roomId);

    // 原子减一（仅在大于0时）
    int decrementMembersIfPositive(@Param("roomId") Long roomId);
} 