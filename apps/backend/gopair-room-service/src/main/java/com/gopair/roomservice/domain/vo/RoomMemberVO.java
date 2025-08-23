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
     * 房间内显示名称
     */
    private String displayName;

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
} 