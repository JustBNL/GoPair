package com.gopair.roomservice.exception;

import com.gopair.common.enums.ErrorCode;
import com.gopair.common.exception.BaseException;

/**
 * 房间异常类
 * 
 * @author gopair
 */
public class RoomException extends BaseException {

    public RoomException(ErrorCode errorCode) {
        super(errorCode);
    }

    public RoomException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public RoomException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
} 