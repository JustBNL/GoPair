package com.gopair.roomservice.service;

import com.gopair.common.core.PageResult;
import com.gopair.roomservice.domain.vo.RoomMemberVO;
import com.gopair.roomservice.domain.vo.RoomVO;
import com.gopair.roomservice.domain.dto.RoomQueryDto;

import java.util.List;

/**
 * 房间成员服务接口
 * 
 * @author gopair
 */
public interface RoomMemberService {

    /**
     * 批量添加房间成员。
     *
     * @param roomId 房间ID
     * @param userIds 要添加的用户ID列表
     * @return 成功添加的数量
     */
    int addMembers(Long roomId, List<Long> userIds);

    /**
     * 添加房间成员
     *
     * @param roomId 房间ID
     * @param userId 用户ID（必须为注册用户）
     * @param role 角色（0-普通成员 1-管理员 2-房主）
     * @return 是否成功
     */
    boolean addMember(Long roomId, Long userId, Integer role);

    /**
     * 移除房间成员
     *
     * @param roomId 房间ID
     * @param userId 用户ID
     * @return 是否成功
     */
    boolean removeMember(Long roomId, Long userId);

    /**
     * 检查用户是否在房间中
     *
     * @param roomId 房间ID
     * @param userId 用户ID
     * @return 是否在房间中
     */
    boolean isMemberInRoom(Long roomId, Long userId);

    /**
     * 获取房间成员列表
     *
     * @param roomId 房间ID
     * @return 成员列表
     */
    List<RoomMemberVO> getRoomMembers(Long roomId);

    /**
     * 获取用户加入的房间列表
     *
     * @param userId 用户ID
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<RoomVO> getUserRooms(Long userId, RoomQueryDto query);

    /**
     * 根据房间ID删除所有成员
     *
     * @param roomId 房间ID
     * @return 是否成功
     */
    boolean deleteByRoomId(Long roomId);

    /**
     * 更新成员最后活跃时间
     *
     * @param roomId 房间ID
     * @param userId 用户ID
     * @return 是否成功
     */
    boolean updateLastActiveTime(Long roomId, Long userId);

    /**
     * 批量更新成员在线状态为离线。
     * 通常在用户所有 WebSocket 连接断开后，由 UserOfflineConsumer 调用。
     * 使用 WHERE status = 0 保证幂等，多次调用效果相同。
     *
     * @param userId 用户ID
     * @return 影响的行数
     */
    int updateStatusToOffline(Long userId);

    /**
     * 标记单个成员离开（软删除）。
     * 将 room_member 的 leave_time 和 leave_type 更新，仅更新仍在房间的成员。
     *
     * * [核心策略]
     * - 幂等：WHERE leave_time IS NULL 保证多次调用不重复更新 leave_time。
     *
     * @param roomId    房间ID
     * @param userId   用户ID
     * @param leaveType 离开类型（1=主动离开 2=被踢出 3=房间关闭被动离开）
     * @return true=标记成功（成员仍在房间）；false=成员不在房间或已离开
     */
    boolean markAsLeft(Long roomId, Long userId, Integer leaveType);

    /**
     * 批量标记房间所有成员离开（软删除）。
     * 通常在房间关闭时调用，将所有仍在房间的成员标记为被动离开。
     *
     * @param roomId    房间ID
     * @param leaveType 离开类型（统一为 3=房间关闭被动离开）
     */
    void markAllAsLeft(Long roomId, Integer leaveType);

    /**
     * 获取房间成员列表（可选择仅返回当前成员或包含历史成员）。
     *
     * @param roomId     房间ID
     * @param activeOnly true=仅返回仍在房间的成员；false=返回全部成员（含已离开的）
     * @return 成员列表
     */
    List<RoomMemberVO> getRoomMembers(Long roomId, boolean activeOnly);
} 