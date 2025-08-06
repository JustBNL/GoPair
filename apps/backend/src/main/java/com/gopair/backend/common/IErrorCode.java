package com.gopair.backend.common;

/**
 * 错误码接口
 * <p>
 * 定义了获取错误码和错误信息的方法，用于统一错误处理
 * </p>
 *
 * @author gopair
 */
public interface IErrorCode {

    /**
     * 获取错误码
     *
     * @return 错误码
     */
    int getCode();

    /**
     * 获取错误信息
     *
     * @return 错误信息
     */
    String getMessage();
} 