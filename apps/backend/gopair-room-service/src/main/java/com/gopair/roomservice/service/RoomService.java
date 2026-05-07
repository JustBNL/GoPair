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
     * 查找过期房间（已被定时任务弃用，现改为查询已关闭超过24小时待清理的房间）。
     *
     * @return 过期房间列表
     */
    List<Room> findExpiredRooms();

    /**
     * 查询需要清理的房间：已关闭超过24小时。
     * 用于定时任务分批查询待清理的房间，不包含 room_member 清理。
     *
     * @return 待清理房间列表
     */
    List<Room> findRoomsToClean();

    /**
     * 清理房间资源（消息、文件、语音通话）。
     * room 和 room_member 永久保留，不在此方法中删除。
     * 由定时任务统一调用，作为清理流程的入口。
     *
     * @param roomId 房间ID
     * @return 实际清理的记录总数（仅供参考）
     */
    int cleanupRoomResources(Long roomId);

    /**
     * 完全删除房间（包括成员）
     * <p>注意：此方法已不再被定时任务调用（room_member 永久保留）。
     * 此方法仅保留用于极端清理场景（如 GDPR 合规数据删除）。
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
     * 更新房间密码可见性（仅房主）
     * <p>专门处理可见性切换，不涉及密码模式变更。
     *
     * @param roomId  房间ID
     * @param userId  操作用户ID（必须是房主）
     * @param visible 密码是否可见（0-隐藏 1-显示）
     */
    void updatePasswordVisibility(Long roomId, Long userId, Integer visible);

    /**
     * 检查用户是否为房间成员
     *
     * @param roomId 房间ID
     * @param userId 用户ID
     * @return 是否为房间成员
     */
    boolean isMemberInRoom(Long roomId, Long userId);
}
