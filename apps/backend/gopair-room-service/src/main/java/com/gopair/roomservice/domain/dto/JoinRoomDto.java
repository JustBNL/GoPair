package com.gopair.roomservice.domain.dto;

import lombok.Data;

/**
 * 加入房间DTO
 * 
 * @author gopair
 */
@Data
public class JoinRoomDto {

    /**
     * 房间邀请码
     */
    private String roomCode;

    /**
     * 房间内显示名称
     */
    private String displayName;
} 