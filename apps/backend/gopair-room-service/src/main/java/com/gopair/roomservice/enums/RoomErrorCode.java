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
    ROOM_STATE_CHANGED(20212, "房间状态已变更，请重试"),

    /**
     * 房间密码错误
     */
    PASSWORD_WRONG(20213, "房间密码错误"),

    /**
     * 请输入房间密码
     */
    PASSWORD_REQUIRED(20214, "请输入房间密码"),

    /**
     * 权限不足，仅房主可执行此操作
     */
    NO_PERMISSION(20215, "权限不足，仅房主可执行此操作"),

    /**
     * 房间已关闭，无需重复操作
     */
    ROOM_ALREADY_CLOSED(20216, "房间已关闭，无需重复操作"),

    /**
     * 参数无效
     */
    PARAM_INVALID(20217, "参数无效"),

    /**
     * 房间已过期，进入只读模式
     */
    ROOM_READ_ONLY(20218, "房间已过期，进入只读模式"),

    /**
     * 房间已归档，无法操作
     */
    ROOM_ARCHIVED(20219, "房间已归档，无法操作"),

    /**
     * 房间已过期，无需重复操作
     */
    ALREADY_EXPIRED(20220, "房间已过期，无需重复操作"),

    /**
     * 房间已归档，无法执行此操作
     */
    ALREADY_ARCHIVED(20221, "房间已归档，无法执行此操作"),

    /**
     * 已过期房间无法直接关闭，请先续期
     */
    EXPIRED_CANNOT_CLOSE(20222, "已过期房间无法直接关闭，请先续期"),

    /**
     * 房间已归档，无法续期
     */
    ARCHIVED_CANNOT_RENEW(20223, "房间已归档，无法续期"),

    /**
     * 房间已关闭，无法续期
     */
    CLOSED_CANNOT_RENEW(20224, "房间已关闭，无法续期");

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
