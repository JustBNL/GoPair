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
     * 查询需要归档的房间：已关闭超过阈值时间，资源清理后写入 status=3。
     *
     * @param thresholdTime 阈值时间（当前时间 - ARCHIVE_THRESHOLD_HOURS 小时）
     * @return 待归档房间列表
     */
    List<Room> selectRoomsToArchive(@Param("thresholdTime") LocalDateTime thresholdTime);

    /**
     * 查询已过期且超过归档前置阈值（30天）的房间，用于系统自动关闭。
     *
     * @param thresholdTime 阈值时间（当前时间 - EXPIRED_TO_CLOSED_DAYS 天）
     * @return 待关闭的过期房间列表
     */
    List<Room> selectExpiredRoomsToClose(@Param("thresholdTime") LocalDateTime thresholdTime);

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
     * 查询用户参与的房间列表（带用户角色、加入时间），支持分页和历史房间过滤。
     *
     * @param userId         当前用户 ID
     * @param status         房间状态（null 表示不限状态）
     * @param includeHistory  是否包含历史房间（true=IN(0,1,2)，false/null=IN(0)）
     * @param offset         偏移量
     * @param limit          每页大小
     * @return 房间 VO 列表，userRole/joinTime 已填充
     */
    List<RoomVO> selectUserRoomsPage(@Param("userId") Long userId,
                                     @Param("status") Integer status,
                                     @Param("includeHistory") Boolean includeHistory,
                                     @Param("offset") int offset,
                                     @Param("limit") int limit);

    /**
     * 统计用户参与的房间数量（支持状态过滤），用于分页计数。
     *
     * @param userId         用户 ID
     * @param status         房间状态（null 表示不限状态）
     * @param includeHistory 是否包含历史房间（true=IN(0,1,2)，false/null=IN(0)）
     * @return 符合条件的房间数量
     */
    Long countUserRoomsWithRelationship(@Param("userId") Long userId,
                                       @Param("status") Integer status,
                                       @Param("includeHistory") Boolean includeHistory);

    /**
     * 仅更新房间状态和关闭时间（避免幽灵更新）
     *
     * @param roomId     房间ID
     * @param status     目标状态
     * @param closedTime 关闭时间
     * @return 更新行数
     */
    int updateStatusAndClosedTime(@Param("roomId") Long roomId,
                                  @Param("status") Integer status,
                                  @Param("closedTime") LocalDateTime closedTime);

    /**
     * 仅更新密码可见性（避免幽灵更新）
     *
     * @param roomId        房间ID
     * @param passwordVisible 密码可见性
     * @return 更新行数
     */
    int updatePasswordVisible(@Param("roomId") Long roomId,
                              @Param("passwordVisible") Integer passwordVisible);

    /**
     * 仅更新房间状态（无条件，用于归档操作）。
     *
     * @param roomId 房间ID
     * @param status 目标状态
     * @return 更新行数
     */
    int updateStatus(@Param("roomId") Long roomId, @Param("status") Integer status);

    /**
     * 更新房间过期时间和状态（用于续期）。
     * WHERE status IN (0,2) 确保仅 ACTIVE/EXPIRED 可续期。
     *
     * @param roomId     房间ID
     * @param expireTime 新的过期时间
     * @param status     目标状态（续期后固定为 ACTIVE，即0）
     * @return 更新行数（0 表示房间不存在或状态不符合条件）
     */
    int updateExpireTimeAndStatus(@Param("roomId") Long roomId,
                                  @Param("expireTime") LocalDateTime expireTime,
                                  @Param("status") Integer status);
} 