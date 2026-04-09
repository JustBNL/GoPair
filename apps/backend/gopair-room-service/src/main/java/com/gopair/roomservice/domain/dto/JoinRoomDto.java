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
     * 加入密码（passwordMode != 0 时必填）
     */
    private String password;
} 