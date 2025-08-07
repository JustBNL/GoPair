package com.gopair.backend.controller.common;

import com.gopair.backend.common.constants.MessageConstants;
import com.gopair.backend.common.core.R;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 通用控制器
 * 
 * 所有控制器的基类，提供通用方法
 * 
 * @author gopair
 */
public class BaseController {
    
    protected final Logger logger = LoggerFactory.getLogger(this.getClass());
    
    /**
     * 返回成功
     * 
     * @return 成功响应
     */
    protected <T> R<T> success() {
        return R.ok();
    }
    
    /**
     * 返回成功数据
     * 
     * @param data 数据
     * @return 成功响应
     */
    protected <T> R<T> success(T data) {
        return R.ok(data);
    }
    
    /**
     * 返回成功消息和数据
     * 
     * @param message 消息
     * @param data 数据
     * @return 成功响应
     */
    protected <T> R<T> success(String message, T data) {
        return R.ok(message, data);
    }
    
    /**
     * 返回错误
     * 
     * @return 错误响应
     */
    protected <T> R<T> error() {
        return R.fail();
    }
    
    /**
     * 返回错误消息
     * 
     * @param message 错误消息
     * @return 错误响应
     */
    protected <T> R<T> error(String message) {
        return R.fail(message);
    }
    
    /**
     * 返回错误码和消息
     * 
     * @param code 错误码
     * @param message 错误消息
     * @return 错误响应
     */
    protected <T> R<T> error(int code, String message) {
        return R.fail(code, message);
    }
    
    /**
     * 页面跳转
     * 
     * @param url 跳转地址
     * @return 跳转地址
     */
    protected String redirect(String url) {
        return "redirect:" + url;
    }
} 