package com.gopair.roomservice.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 房间信息VO
 * 
 * @author gopair
 */
@Data
public class RoomVO {

    /**
     * 房间ID
     */
    private Long roomId;

    /**
     * 房间邀请码
     */
    private String roomCode;

    /**
     * 房间名称
     */
    private String roomName;

    /**
     * 房间描述
     */
    private String description;

    /**
     * 最大成员数
     */
    private Integer maxMembers;

    /**
     * 当前成员数
     */
    private Integer currentMembers;

    /**
     * 房主用户ID
     */
    private Long ownerId;

    /**
     * 房间状态（0-活跃 1-已关闭）
     */
    private Integer status;

    /**
     * 房间过期时间
     */
    private LocalDateTime expireTime;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
} 