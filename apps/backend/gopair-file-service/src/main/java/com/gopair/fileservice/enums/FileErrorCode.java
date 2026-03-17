package com.gopair.fileservice.enums;

import com.gopair.common.enums.ErrorCode;

/**
 * 文件服务错误码枚举
 *
 * 错误码规范：50000-50099
 *
 * @author gopair
 */
public enum FileErrorCode implements ErrorCode {

    /**
     * 文件不存在
     */
    FILE_NOT_FOUND(50000, "文件不存在"),

    /**
     * 文件大小超出限制
     */
    FILE_TOO_LARGE(50001, "文件大小超出限制"),

    /**
     * 不支持的文件类型
     */
    FILE_TYPE_NOT_ALLOWED(50002, "不支持的文件类型"),

    /**
     * 文件上传失败
     */
    FILE_UPLOAD_FAILED(50003, "文件上传失败"),

    /**
     * 房间存储空间已满
     */
    ROOM_QUOTA_EXCEEDED(50004, "房间存储空间已满，无法继续上传"),

    /**
     * 无权限操作该文件
     */
    FILE_ACCESS_DENIED(50005, "无权限操作该文件"),

    /**
     * MinIO操作异常
     */
    MINIO_OPERATION_FAILED(50006, "存储服务操作失败");

    private final int code;
    private final String message;

    FileErrorCode(int code, String message) {
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
