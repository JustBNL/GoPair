package com.gopair.roomservice.service;

import com.gopair.common.core.PageResult;
import com.gopair.common.entity.BaseQuery;
import com.gopair.roomservice.domain.dto.JoinRoomDto;
import com.gopair.roomservice.domain.dto.RoomDto;
import com.gopair.roomservice.domain.po.Room;
import com.gopair.roomservice.domain.vo.RoomMemberVO;
import com.gopair.roomservice.domain.vo.RoomVO;
import com.gopair.roomservice.domain.vo.JoinAcceptedVO;
import com.gopair.roomservice.service.JoinResultQueryService.JoinStatusVO;

import java.util.List;

/**
 * 房间服务接口
 * 
 * @author gopair
 */
public interface RoomService {

    /**
     * 创建房间
     *
     * @param roomDto 房间信息
     * @param userId 用户ID（必须为注册用户）
     * @return 房间信息
     */
    RoomVO createRoom(RoomDto roomDto, Long userId);

    /**
     * 加入房间
     *
     * @param joinRoomDto 加入房间信息
     * @param userId 用户ID（必须为注册用户）
     * @return 房间信息
     */
    RoomVO joinRoom(JoinRoomDto joinRoomDto, Long userId);

    /**
     * 离开房间
     *
     * @param roomId 房间ID
     * @param userId 用户ID
     * @return 是否成功
     */
    boolean leaveRoom(Long roomId, Long userId);

    /**
     * 根据房间码查询房间
     *
     * @param roomCode 房间码
     * @return 房间信息
     */
    RoomVO getRoomByCode(String roomCode);

    /**
     * 查询用户的房间列表
     *
     * @param userId 用户ID
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<RoomVO> getUserRooms(Long userId, BaseQuery query);

    /**
     * 关闭房间
     *
     * @param roomId 房间ID
     * @param userId 用户ID
     * @return 是否成功
     */
    boolean closeRoom(Long roomId, Long userId);

    /**
     * 获取房间成员列表
     *
     * @param roomId 房间ID
     * @return 成员列表
     */
    List<RoomMemberVO> getRoomMembers(Long roomId);

    /**
     * 查找过期房间
     *
     * @return 过期房间列表
     */
    List<Room> findExpiredRooms();

    /**
     * 完全删除房间（包括成员）
     *
     * @param roomId 房间ID
     * @return 是否成功
     */
    boolean deleteRoomCompletely(Long roomId);

    /**
     * 检查房间码是否唯一
     *
     * @param roomCode 房间码
     * @return 是否唯一
     */
    boolean isRoomCodeUnique(String roomCode);

    // 新增：异步加入能力
    JoinAcceptedVO joinRoomAsync(JoinRoomDto joinRoomDto, Long userId);

    JoinStatusVO queryJoinResult(String token);
} 