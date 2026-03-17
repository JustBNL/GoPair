package com.gopair.fileservice.exception;

import com.gopair.common.exception.BaseException;
import com.gopair.fileservice.enums.FileErrorCode;

/**
 * 文件服务业务异常
 *
 * @author gopair
 */
public class FileException extends BaseException {

    public FileException(FileErrorCode errorCode) {
        super(errorCode);
    }

    public FileException(FileErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public FileException(FileErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }
}
