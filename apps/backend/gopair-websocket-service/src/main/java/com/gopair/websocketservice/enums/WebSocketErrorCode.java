package com.gopair.websocketservice.enums;

import com.gopair.common.enums.ErrorCode;

/**
 * WebSocket服务错误码枚举类
 * 
 * 实现ErrorCode接口，定义WebSocket服务特有的错误码和错误信息。
 * 错误码规则：采用5位数字格式 A-BB-CCC
 * - A: 错误级别 (2=业务级错误)
 * - BB: 服务标识 (05=WebSocket服务)
 * - CCC: 具体错误序号，从000开始连续递增
 * 
 * WebSocket服务错误码范围：20500-20599
 * 
 * @author gopair
 */
public enum WebSocketErrorCode implements ErrorCode {

    /**
     * 连接相关错误码
     */
    USER_INFO_HEADER_MISSING(20500, "用户信息请求头缺失"),
    USER_INFO_FORMAT_ERROR(20501, "用户信息格式错误"),
    ROOM_ID_INVALID(20502, "房间ID参数无效"),
    CONNECTION_ESTABLISH_FAILED(20503, "WebSocket连接建立失败"),
    
    /**
     * 消息处理相关错误码
     */
    MESSAGE_PROCESSING_ERROR(20510, "消息处理失败"),
    SESSION_NOT_FOUND(20511, "会话不存在"),
    HEARTBEAT_TIMEOUT(20512, "心跳超时");

    /**
     * 错误码
     */
    private final int code;
    
    /**
     * 错误信息
     */
    private final String message;

    /**
     * 构造函数
     *
     * @param code    错误码
     * @param message 错误信息
     */
    WebSocketErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    /**
     * 获取错误码
     *
     * @return 错误码
     */
    @Override
    public int getCode() {
        return code;
    }

    /**
     * 获取错误信息
     *
     * @return 错误信息
     */
    @Override
    public String getMessage() {
        return message;
    }
} 