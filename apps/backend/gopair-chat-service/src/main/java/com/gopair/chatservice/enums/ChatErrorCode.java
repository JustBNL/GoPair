package com.gopair.chatservice.enums;

import com.gopair.common.enums.ErrorCode;

/**
 * 聊天服务错误码枚举类。
 *
 * <p>错误码规则：采用5位数字格式 A-BB-CCC
 * <ul>
 *   <li>A: 错误级别 (2=业务级错误)</li>
 *   <li>BB: 服务标识 (06=聊天服务)</li>
 *   <li>CCC: 具体错误序号</li>
 * </ul>
 *
 * <p>聊天服务错误码范围：20600-20699
 *
 * @author gopair
 */
public enum ChatErrorCode implements ErrorCode {

    FRIEND_REQUEST_NOT_FOUND(20600, "好友申请不存在"),
    FRIEND_REQUEST_ALREADY_PROCESSED(20601, "好友申请已被处理"),
    CANNOT_ADD_SELF(20602, "不能添加自己为好友"),
    ALREADY_FRIENDS(20603, "你们已经是好友了"),
    FRIEND_REQUEST_ALREADY_EXISTS(20604, "已存在待处理的好友申请"),
    CANNOT_FRIEND_REQUEST_YOURSELF(20605, "不能向自己发送好友申请"),
    NOT_YOUR_REQUEST(20606, "这不是你的好友申请"),
    FRIEND_NOT_FOUND(20607, "好友关系不存在"),
    PRIVATE_MESSAGE_NOT_FOUND(20608, "私聊消息不存在"),
    NO_PERMISSION_DELETE_MESSAGE(20609, "无权限删除该消息"),
    NO_PERMISSION_RECALL_MESSAGE(20610, "无权限撤回该消息"),
    RECALL_TIME_EXPIRED(20611, "撤回时间已超过2分钟"),
    MESSAGE_ALREADY_RECALLED(20612, "消息已被撤回"),
    CONTENT_TOO_LONG(20613, "消息内容超过最大长度限制"),
    NOT_FRIENDS(20614, "你们还不是好友，无法发送私聊消息"),
    USER_NOT_FOUND(20615, "用户不存在"),
    MESSAGE_TYPE_INVALID(20616, "消息类型无效"),
    MESSAGE_CONTENT_EMPTY(20617, "消息内容不能为空"),
    FILE_URL_EMPTY(20618, "文件URL不能为空");

    private final int code;
    private final String message;

    ChatErrorCode(int code, String message) {
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
