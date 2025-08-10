package com.gopair.common.enums;

/**
 * 错误码接口
 * 
 * 定义错误码的标准接口，所有错误码枚举都应该实现此接口
 * 
 * @author gopair
 */
public interface ErrorCode {

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
