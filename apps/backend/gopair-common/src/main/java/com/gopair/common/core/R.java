package com.gopair.common.core;

import com.gopair.common.enums.ErrorCode;
import com.gopair.common.constants.MessageConstants;

import java.io.Serializable;

/**
 * 响应信息主体
 * 
 * 参考若依框架设计，统一返回结果处理
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
     * 初始化一个新创建的 R 对象
     * 
     * @param code 状态码
     * @param msg  返回消息
     * @param data 返回数据
     */
    public R(int code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    /**
     * 返回成功消息
     * 
     * @return 成功消息
     */
    public static <T> R<T> ok() {
        return new R<>(SUCCESS, MessageConstants.SUCCESS, null);
    }

    /**
     * 返回成功数据
     * 
     * @param data 返回数据
     * @return 成功消息
     */
    public static <T> R<T> ok(T data) {
        return new R<>(SUCCESS, MessageConstants.SUCCESS, data);
    }

    /**
     * 返回成功消息
     * 
     * @param msg  返回消息
     * @param data 返回数据
     * @return 成功消息
     */
    public static <T> R<T> ok(String msg, T data) {
        return new R<>(SUCCESS, msg, data);
    }

    /**
     * 返回错误消息
     * 
     * @return 错误消息
     */
    public static <T> R<T> fail() {
        return new R<>(ERROR, MessageConstants.FAILED, null);
    }

    /**
     * 返回错误消息
     * 
     * @param msg 返回消息
     * @return 错误消息
     */
    public static <T> R<T> fail(String msg) {
        return new R<>(ERROR, msg, null);
    }

    /**
     * 返回错误消息
     * 
     * @param code 状态码
     * @param msg  返回消息
     * @return 错误消息
     */
    public static <T> R<T> fail(int code, String msg) {
        return new R<>(code, msg, null);
    }

    /**
     * 返回失败结果
     *
     * @param errorCode 错误码
     * @param <T>       数据类型
     * @return 响应结果
     */
    public static <T> R<T> fail(ErrorCode errorCode) {
        return new R<>(errorCode.getCode(), errorCode.getMessage(), null);
    }

    /**
     * 是否为成功状态
     * 
     * @return 是否成功
     */
    public boolean isSuccess() {
        return code == SUCCESS;
    }

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