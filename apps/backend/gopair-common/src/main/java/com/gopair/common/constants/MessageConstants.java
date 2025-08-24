package com.gopair.common.constants;

/**
 * 消息常量类
 * 
 * 统一管理系统中的提示信息常量，便于维护和国际化
 * 
 * @author gopair
 */
public class MessageConstants {

    /**
     * 通用消息
     */
    public static final String SUCCESS = "操作成功";
    public static final String FAILED = "操作失败";
    
    /**
     * 认证相关消息
     */
    public static final String INVALID_CREDENTIALS = "用户名或密码错误";
    public static final String PASSWORD_ERROR = "密码错误";
    public static final String UNAUTHORIZED = "未授权访问";
    
    /**
     * 用户相关消息
     */
    // 用户状态相关消息
    public static final String USER_NOT_FOUND = "用户不存在";
    public static final String USER_ALREADY_EXISTS = "用户已存在";
    public static final String EMAIL_ALREADY_EXISTS = "邮箱已存在";
    public static final String NICKNAME_ALREADY_EXISTS = "昵称已存在";
    
    /**
     * 参数校验相关消息
     */
    public static final String PARAM_ERROR = "参数错误";
    public static final String PARAM_MISSING = "缺少必要参数";
    
    /**
     * 字段验证相关消息
     */
    public static final String NICKNAME_LENGTH_ERROR = "昵称长度必须在1-20个字符之间";
    public static final String PASSWORD_LENGTH_ERROR = "密码长度必须在6-50个字符之间";
    public static final String EMAIL_FORMAT_ERROR = "请输入有效的邮箱地址";
    
    /**
     * 系统错误相关消息
     */
    public static final String SYSTEM_ERROR = "系统内部错误";
    public static final String SERVICE_UNAVAILABLE = "服务不可用";
    
    /**
     * 业务错误相关消息
     */
    public static final String BUSINESS_ERROR = "业务处理异常";
    public static final String RESOURCE_NOT_FOUND = "请求资源不存在";
    
    /**
     * 权限相关消息
     */
    public static final String NO_PERMISSION = "无操作权限";
    
    /**
     * 房间业务消息
     */
    public static final String ROOM_NOT_FOUND = "房间不存在";
    public static final String ROOM_CODE_INVALID = "房间邀请码无效";
    public static final String ROOM_FULL = "房间已满";
    public static final String ROOM_EXPIRED = "房间已过期";
    public static final String ALREADY_IN_ROOM = "已在房间中";
    public static final String NOT_IN_ROOM = "不在房间中";
    public static final String ROOM_CODE_GENERATION_FAILED = "房间码生成失败";
    public static final String ROOM_NAME_EMPTY = "房间名称不能为空";
    public static final String NICKNAME_EMPTY = "昵称不能为空";
    
    /**
     * 网关认证消息
     */
    public static final String TOKEN_NOT_FOUND = "未找到认证令牌";
    public static final String TOKEN_VALIDATION_FAILED = "令牌验证失败";
    public static final String INVALID_USER_INFO = "无效的用户信息";
    public static final String AUTH_PROCESSING_ERROR = "认证处理异常";
    
    /**
     * 业务操作相关消息
     */
    public static final String USER_NOT_LOGGED_IN = "用户未登录";
    public static final String DISPLAY_NAME_EMPTY = "显示名称不能为空";
    public static final String ROOM_CLOSED = "房间已关闭";
    public static final String ROOM_STATE_CHANGED = "房间状态已变更，请重试";
    public static final String ROOM_CREATION_FAILED = "房间创建失败";
    public static final String USER_PREFIX = "用户";
    
    /**
     * 私有构造函数，防止实例化
     */
    private MessageConstants() {
        throw new IllegalStateException("常量类不允许实例化");
    }
} 