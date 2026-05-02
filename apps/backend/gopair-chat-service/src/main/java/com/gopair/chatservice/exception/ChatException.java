package com.gopair.chatservice.exception;

import com.gopair.chatservice.enums.ChatErrorCode;
import com.gopair.common.exception.BaseException;

/**
 * 聊天服务业务异常。
 *
 * @author gopair
 */
public class ChatException extends BaseException {

    public ChatException(ChatErrorCode errorCode) {
        super(errorCode);
    }

    public ChatException(ChatErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public ChatException(ChatErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }

    public ChatException(ChatErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
