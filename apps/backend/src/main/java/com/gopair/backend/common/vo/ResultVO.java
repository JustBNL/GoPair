package com.gopair.backend.common.vo;

import com.gopair.backend.common.IErrorCode;
import lombok.Data;

import java.io.Serializable;

/**
 * 通用API响应封装类
 * <p>
 * 该类用于统一封装后端API的响应结果，包含状态码、消息和数据三部分。
 * 通过静态工厂方法模式，简化Controller层代码，提高可读性和可维护性。
 * </p>
 *
 * @param <T> 响应数据的类型参数
 * @author gopair
 */
@Data
public class ResultVO<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 成功状态码：200
     */
    public static final int SUCCESS_CODE = 200;

    /**
     * 错误状态码：500
     */
    public static final int ERROR_CODE = 500;

    /**
     * 状态码
     */
    private int code;

    /**
     * 响应消息
     */
    private String message;

    /**
     * 响应数据
     */
    private T data;

    /**
     * 私有构造函数，强制使用静态工厂方法创建实例
     *
     * @param code    状态码
     * @param message 响应消息
     * @param data    响应数据
     */
    private ResultVO(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /**
     * 创建成功响应（无数据）
     *
     * @param <T> 响应数据类型
     * @return 成功的响应对象
     */
    public static <T> ResultVO<T> success() {
        return new ResultVO<>(SUCCESS_CODE, "操作成功", null);
    }

    /**
     * 创建成功响应（有数据）
     *
     * @param data 响应数据
     * @param <T>  响应数据类型
     * @return 包含数据的成功响应对象
     */
    public static <T> ResultVO<T> success(T data) {
        return new ResultVO<>(SUCCESS_CODE, "操作成功", data);
    }

    /**
     * 创建成功响应（自定义消息和数据）
     *
     * @param message 自定义成功消息
     * @param data    响应数据
     * @param <T>     响应数据类型
     * @return 包含自定义消息和数据的成功响应对象
     */
    public static <T> ResultVO<T> success(String message, T data) {
        return new ResultVO<>(SUCCESS_CODE, message, data);
    }

    /**
     * 创建通用错误响应
     *
     * @param <T> 响应数据类型
     * @return 通用错误响应对象
     */
    public static <T> ResultVO<T> error() {
        return new ResultVO<>(ERROR_CODE, "操作失败", null);
    }

    /**
     * 创建带自定义错误消息的错误响应
     *
     * @param message 自定义错误消息
     * @param <T>     响应数据类型
     * @return 包含自定义错误消息的错误响应对象
     */
    public static <T> ResultVO<T> error(String message) {
        return new ResultVO<>(ERROR_CODE, message, null);
    }

    /**
     * 创建带自定义错误码和错误消息的错误响应
     *
     * @param code    自定义错误码
     * @param message 自定义错误消息
     * @param <T>     响应数据类型
     * @return 包含自定义错误码和错误消息的错误响应对象
     */
    public static <T> ResultVO<T> error(int code, String message) {
        return new ResultVO<>(code, message, null);
    }

    /**
     * 从错误码接口创建错误响应
     *
     * @param errorCode 错误码接口实现
     * @param <T>       响应数据类型
     * @return 基于错误码接口的错误响应对象
     */
    public static <T> ResultVO<T> error(IErrorCode errorCode) {
        return new ResultVO<>(errorCode.getCode(), errorCode.getMessage(), null);
    }
} 