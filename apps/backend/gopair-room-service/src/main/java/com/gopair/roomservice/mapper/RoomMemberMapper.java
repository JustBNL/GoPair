package com.gopair.roomservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gopair.roomservice.domain.po.RoomMember;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

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

    /**
     * 标记成员离开（软删除）。
     * 仅更新 leave_time 为空的记录，幂等保证。
     *
     * @param roomId    房间ID
     * @param userId   用户ID
     * @param leaveTime 离开时间
     * @param leaveType 离开类型
     * @return 影响的行数
     */
    int markAsLeft(@Param("roomId") Long roomId,
                   @Param("userId") Long userId,
                   @Param("leaveTime") LocalDateTime leaveTime,
                   @Param("leaveType") Integer leaveType);

    /**
     * 批量标记房间所有成员离开（软删除）。
     * 仅更新 leave_time 为空的记录。
     *
     * @param roomId    房间ID
     * @param leaveTime 离开时间
     * @param leaveType 离开类型
     * @return 影响的行数
     */
    int markAllAsLeft(@Param("roomId") Long roomId,
                      @Param("leaveTime") LocalDateTime leaveTime,
                      @Param("leaveType") Integer leaveType);

    /**
     * 重新激活历史成员记录，清空 leave_time 和 leave_type。
     * 用于退出后重新加入房间的场景，绕过 MyBatis-Plus updateById 对 null 字段的忽略策略。
     * 仅更新 leave_time 非空的记录，幂等保证。
     *
     * @param roomId        房间ID
     * @param userId        用户ID
     * @param status        成员状态（在线）
     * @param joinTime      加入时间
     * @param lastActiveTime 最后活跃时间
     * @return 影响的行数
     */
    int reactivateMember(@Param("roomId") Long roomId,
                         @Param("userId") Long userId,
                         @Param("status") Integer status,
                         @Param("joinTime") LocalDateTime joinTime,
                         @Param("lastActiveTime") LocalDateTime lastActiveTime);
} 