package com.gopair.common.core;

import com.gopair.common.enums.ErrorCode;
import com.gopair.common.constants.MessageConstants;

import java.io.Serializable;

/**
 * 统一响应结果封装类
 * 
 * 参考若依框架设计，只负责响应结果的构建和封装
 * 
 * @author gopair
 */
public class R<T> implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 成功状态码
     */
    public static final int SUCCESS = 200;

    /**
     * 失败状态码
     */
    public static final int ERROR = 500;

    /**
     * 状态码
     */
    private int code;

    /**
     * 返回消息
     */
    private String msg;

    /**
     * 返回数据
     */
    private T data;

    /**
     * 私有构造函数
     * 
     * @param code 状态码
     * @param msg  返回消息
     * @param data 返回数据
     */
    private R(int code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    // ==================== 成功响应构建 ====================

    /**
     * 返回成功消息
     */
    public static <T> R<T> ok() {
        return new R<>(SUCCESS, MessageConstants.SUCCESS, null);
    }

    /**
     * 返回成功数据
     */
    public static <T> R<T> ok(T data) {
        return new R<>(SUCCESS, MessageConstants.SUCCESS, data);
    }

    /**
     * 返回成功消息
     */
    public static <T> R<T> ok(String msg, T data) {
        return new R<>(SUCCESS, msg, data);
    }

    // ==================== 失败响应构建 ====================

    /**
     * 返回默认错误消息
     */
    public static <T> R<T> fail() {
        return new R<>(ERROR, MessageConstants.FAILED, null);
    }

    /**
     * 返回自定义错误消息
     */
    public static <T> R<T> fail(String msg) {
        return new R<>(ERROR, msg, null);
    }

    /**
     * 返回指定状态码和消息的错误
     */
    public static <T> R<T> fail(int code, String msg) {
        return new R<>(code, msg, null);
    }

    /**
     * 根据错误码构建失败响应
     */
    public static <T> R<T> fail(ErrorCode errorCode) {
        return new R<>(errorCode.getCode(), errorCode.getMessage(), null);
    }

    /**
     * 根据错误码和自定义消息构建失败响应
     */
    public static <T> R<T> fail(ErrorCode errorCode, String customMessage) {
        return new R<>(errorCode.getCode(), customMessage, null);
    }

    // ==================== 工具方法 ====================

    /**
     * 是否为成功状态
     */
    public boolean isSuccess() {
        return code == SUCCESS;
    }

    /**
     * 转换为JSON字符串
     */
    public String toJson() {
        return String.format(
            "{\"code\":%d,\"msg\":\"%s\",\"data\":%s}",
            code,
            msg.replace("\"", "\\\""),
            data == null ? "null" : "\"" + data.toString().replace("\"", "\\\"") + "\""
        );
    }

    // ==================== Getter/Setter ====================

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }
} 