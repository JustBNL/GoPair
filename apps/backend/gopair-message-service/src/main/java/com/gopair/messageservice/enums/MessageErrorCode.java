package com.gopair.messageservice.enums;


import com.gopair.common.enums.ErrorCode;

/**
 * 消息服务错误码枚举类
 *
 * 实现ErrorCode接口，定义消息服务特有的错误码和错误信息。
 * 错误码规则：采用5位数字格式 A-BB-CCC
 * - A: 错误级别 (2=业务级错误)
 * - BB: 服务标识 (04=消息服务)
 * - CCC: 具体错误序号，从000开始连续递增
 *
 * 消息服务错误码范围：20400-20499
 *
 * @author gopair
 */
public enum MessageErrorCode implements ErrorCode {

    /**
     * 消息不存在
     */
    MESSAGE_NOT_FOUND(20400, "消息不存在"),

    /**
     * 消息类型无效
     */
    MESSAGE_TYPE_INVALID(20401, "消息类型无效"),

    /**
     * 消息内容不能为空
     */
    MESSAGE_CONTENT_EMPTY(20402, "消息内容不能为空"),

    /**
     * 用户不在房间中
     */
    USER_NOT_IN_ROOM(20403, "用户不在该房间内，无法发送消息"),

    /**
     * 无权限删除消息
     */
    NO_PERMISSION_DELETE_MESSAGE(20404, "无权限删除该消息"),

    /**
     * 文件URL不能为空
     */
    FILE_URL_EMPTY(20405, "文件消息的文件URL不能为空"),

    /**
     * 文件名不能为空
     */
    FILE_NAME_EMPTY(20406, "文件消息的文件名不能为空"),

    /**
     * 消息发送失败
     */
    MESSAGE_SEND_FAILED(20407, "消息发送失败"),

    /**
     * 消息内容超过最大长度限制
     */
    MESSAGE_CONTENT_TOO_LONG(20408, "消息内容超过最大长度限制"),

    /**
     * 消息撤回时间已超限（超过2分钟）
     */
    MESSAGE_RECALL_TIME_EXPIRED(20409, "撤回时间已超过2分钟"),

    /**
     * 消息已被撤回
     */
    MESSAGE_ALREADY_RECALLED(20410, "消息已被撤回");

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
    MessageErrorCode(int code, String message) {
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
