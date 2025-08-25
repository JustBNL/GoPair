package com.gopair.roomservice.enums;

import com.gopair.common.enums.ErrorCode;

/**
 * 房间服务错误码枚举类
 * 
 * 实现ErrorCode接口，定义房间服务特有的错误码和错误信息。
 * 错误码规则：采用5位数字格式 A-BB-CCC
 * - A: 错误级别 (2=业务级错误)
 * - BB: 服务标识 (02=房间服务)
 * - CCC: 具体错误序号，从000开始连续递增
 * 
 * 房间服务错误码范围：20200-20299
 * 
 * @author gopair
 */
public enum RoomErrorCode implements ErrorCode {

    /**
     * 房间不存在
     */
    ROOM_NOT_FOUND(20200, "房间不存在"),

    /**
     * 房间邀请码无效
     */
    ROOM_CODE_INVALID(20201, "房间邀请码无效"),

    /**
     * 房间已满
     */
    ROOM_FULL(20202, "房间已满"),

    /**
     * 房间已过期
     */
    ROOM_EXPIRED(20203, "房间已过期"),

    /**
     * 已在房间中
     */
    ALREADY_IN_ROOM(20204, "已在房间中"),

    /**
     * 不在房间中
     */
    NOT_IN_ROOM(20205, "不在房间中"),

    /**
     * 房间码生成失败
     */
    ROOM_CODE_GENERATION_FAILED(20206, "房间码生成失败"),

    /**
     * 房间名称不能为空
     */
    ROOM_NAME_EMPTY(20207, "房间名称不能为空"),

    /**
     * 昵称不能为空
     */
    NICKNAME_EMPTY(20208, "昵称不能为空"),
    
    /**
     * 用户未登录
     */
    USER_NOT_LOGGED_IN(20209, "用户未登录"),
    
    /**
     * 房间创建失败
     */
    ROOM_CREATION_FAILED(20210, "房间创建失败"),
    
    /**
     * 房间已关闭
     */
    ROOM_CLOSED(20211, "房间已关闭"),
    
    /**
     * 房间状态已变更，请重试
     */
    ROOM_STATE_CHANGED(20212, "房间状态已变更，请重试");

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