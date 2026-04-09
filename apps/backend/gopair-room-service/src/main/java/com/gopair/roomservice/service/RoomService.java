package com.gopair.roomservice.service;

import com.gopair.common.core.PageResult;
import com.gopair.roomservice.domain.dto.JoinRoomDto;
import com.gopair.roomservice.domain.dto.RoomDto;
import com.gopair.roomservice.domain.dto.RoomQueryDto;
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
    PageResult<RoomVO> getUserRooms(Long userId, RoomQueryDto query);

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
     * <p>注意：此方法仅供内部定时任务调用，用于清理过期房间。
     * 由于定时任务在无用户上下文的场景下执行，此方法不进行权限检查。
     * 如需对外暴露，请添加房主权限验证。
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

    /**
     * 更新房间密码设置（仅房主）
     *
     * @param roomId      房间ID
     * @param userId      操作用户ID（必须是房主）
     * @param mode        密码模式（0-关闭 1-固定密码 2-动态令牌）
     * @param rawPassword 明文密码（mode=1时必填，mode=0/2时忽略）
     * @param visible     密码是否可见（0-隐藏 1-显示）
     */
    void updateRoomPassword(Long roomId, Long userId, Integer mode, String rawPassword, Integer visible);

    /**
     * 获取当前房间密码/令牌（仅房主）
     *
     * @param roomId 房间ID
     * @param userId 操作用户ID（必须是房主）
     * @return 当前密码明文或TOTP令牌，以及剩余有效秒数
     */
    RoomVO getRoomCurrentPassword(Long roomId, Long userId);

    /**
     * 踢出房间成员（仅房主）
     *
     * @param roomId       房间ID
     * @param operatorId   操作者ID（必须是房主）
     * @param targetUserId 被踢出的用户ID
     */
    void kickMember(Long roomId, Long operatorId, Long targetUserId);

    /**
     * 检查用户是否为房间成员
     *
     * @param roomId 房间ID
     * @param userId 用户ID
     * @return 是否为房间成员
     */
    boolean isMemberInRoom(Long roomId, Long userId);
}
