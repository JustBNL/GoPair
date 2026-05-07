package com.gopair.roomservice.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 房间成员信息VO
 *
 * @author gopair
 */
@Data
public class RoomMemberVO {

    /**
     * 记录ID
     */
    private Long id;

    /**
     * 房间ID
     */
    private Long roomId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 用户昵称（来自用户服务；用户不存在时降级为「用户{userId}」）
     */
    private String nickname;

    /**
     * 是否为房主（与 role=2 一致，便于前端直接展示）
     */
    private Boolean isOwner;

    /**
     * 角色（0-普通成员 1-管理员 2-房主）
     */
    private Integer role;

    /**
     * 状态（0-在线 1-离线）
     */
    private Integer status;

    /**
     * 加入时间
     */
    private LocalDateTime joinTime;

    /**
     * 最后活跃时间
     */
    private LocalDateTime lastActiveTime;

    /**
     * 离开时间
     */
    private LocalDateTime leaveTime;

    /**
     * 离开类型：1=主动离开 2=被踢出 3=房间关闭被动离开
     */
    private Integer leaveType;

    /**
     * 是否仍在房间（leaveTime 为空则为 true）
     */
    private Boolean isActive;

    /**
     * 用户头像地址
     */
    private String avatar;
}
