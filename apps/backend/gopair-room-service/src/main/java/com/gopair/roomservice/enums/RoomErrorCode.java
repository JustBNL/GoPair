package com.gopair.roomservice.enums;

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
    ROOM_NOT_FOUND(3001, "房间不存在"),

    /**
     * 房间邀请码无效
     */
    ROOM_CODE_INVALID(3002, "房间邀请码无效"),

    /**
     * 房间已满
     */
    ROOM_FULL(3003, "房间已满"),

    /**
     * 房间已过期
     */
    ROOM_EXPIRED(3004, "房间已过期"),

    /**
     * 已在房间中
     */
    ALREADY_IN_ROOM(3005, "已在房间中"),

    /**
     * 不在房间中
     */
    NOT_IN_ROOM(3006, "不在房间中"),

    /**
     * 无操作权限
     */
    NO_PERMISSION(3007, "无操作权限"),

    /**
     * 房间码生成失败
     */
    ROOM_CODE_GENERATION_FAILED(3008, "房间码生成失败"),

    /**
     * 房间名称不能为空
     */
    ROOM_NAME_EMPTY(3009, "房间名称不能为空"),

    /**
     * 昵称不能为空
     */
    NICKNAME_EMPTY(3010, "昵称不能为空"),

    /**
     * 房间成员数量超限
     */
    MEMBERS_COUNT_EXCEEDED(3011, "房间成员数量超限");

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