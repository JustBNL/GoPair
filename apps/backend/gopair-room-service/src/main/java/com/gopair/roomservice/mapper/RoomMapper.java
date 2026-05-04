package com.gopair.roomservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gopair.roomservice.domain.po.Room;
import com.gopair.roomservice.domain.vo.RoomVO;
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

    /**
     * 查询用户参与的房间列表（带用户角色、加入时间、关系类型），单次 JOIN 覆盖所有字段。
     *
     * @param roomIds 当前页的 roomId 列表（已按 createTime DESC 排序）
     * @param userId  当前用户 ID
     * @return 房间 VO 列表，userRole/joinTime/relationshipType 已填充
     */
    List<RoomVO> selectUserRoomsWithRelationship(@Param("roomIds") List<Long> roomIds,
                                                 @Param("userId") Long userId);

    /**
     * 统计用户参与的房间数量（支持状态过滤），用于分页计数。
     *
     * @param userId  用户 ID
     * @param status  房间状态（null 表示不限制状态）
     * @return 符合条件的房间数量
     */
    Long countUserRoomsWithRelationship(@Param("userId") Long userId,
                                        @Param("status") Integer status);

    /**
     * 查询用户参与的所有房间 ID（带状态过滤、按 createTime 降序），用于内存分页前的全量排序。
     *
     * @param userId 用户 ID
     * @param status 房间状态（null 表示不限制状态）
     * @return 按 createTime DESC 排序的房间 ID 列表
     */
    List<Long> selectAllRoomIdsOrderedByUserId(@Param("userId") Long userId,
                                               @Param("status") Integer status);
} 