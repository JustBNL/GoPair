package com.gopair.roomservice.enums;

import com.gopair.common.constants.MessageConstants;
import com.gopair.common.enums.ErrorCode;

/**
 * 房间错误码枚举
 * 
 * @author gopair
 */
public enum RoomErrorCode implements ErrorCode {

    /**
     * 房间不存在
     */
    ROOM_NOT_FOUND(3001, MessageConstants.ROOM_NOT_FOUND),

    /**
     * 房间邀请码无效
     */
    ROOM_CODE_INVALID(3002, MessageConstants.ROOM_CODE_INVALID),

    /**
     * 房间已满
     */
    ROOM_FULL(3003, MessageConstants.ROOM_FULL),

    /**
     * 房间已过期
     */
    ROOM_EXPIRED(3004, MessageConstants.ROOM_EXPIRED),

    /**
     * 已在房间中
     */
    ALREADY_IN_ROOM(3005, MessageConstants.ALREADY_IN_ROOM),

    /**
     * 不在房间中
     */
    NOT_IN_ROOM(3006, MessageConstants.NOT_IN_ROOM),

    /**
     * 房间码生成失败
     */
    ROOM_CODE_GENERATION_FAILED(3008, MessageConstants.ROOM_CODE_GENERATION_FAILED),

    /**
     * 房间名称不能为空
     */
    ROOM_NAME_EMPTY(3009, MessageConstants.ROOM_NAME_EMPTY),

    /**
     * 昵称不能为空
     */
    NICKNAME_EMPTY(3010, MessageConstants.NICKNAME_EMPTY);

    private final int code;
    private final String message;

    RoomErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public int getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }
} 