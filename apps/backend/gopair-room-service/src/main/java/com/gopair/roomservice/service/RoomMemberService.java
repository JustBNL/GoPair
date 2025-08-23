package com.gopair.roomservice.service;

import com.gopair.common.core.PageResult;
import com.gopair.common.entity.BaseQuery;
import com.gopair.roomservice.domain.vo.RoomMemberVO;
import com.gopair.roomservice.domain.vo.RoomVO;

import java.util.List;

/**
 * 房间成员服务接口
 * 
 * @author gopair
 */
public interface RoomMemberService {

    /**
     * 添加房间成员
     *
     * @param roomId 房间ID
     * @param userId 用户ID（必须为注册用户）
     * @param displayName 房间内显示名称
     * @param role 角色（0-普通成员 1-管理员 2-房主）
     * @return 是否成功
     */
    boolean addMember(Long roomId, Long userId, String displayName, Integer role);

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
    PageResult<RoomVO> getUserRooms(Long userId, BaseQuery query);

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
} 