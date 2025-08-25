package com.gopair.common.constants;

/**
 * 消息常量类
 * 
 * 统一管理系统中的提示信息常量，便于维护和国际化
 * 注意：错误信息已迁移到对应的ErrorCode枚举中，此处主要保留验证、通用操作等常量
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
     * 字段验证相关消息
     */
    public static final String NICKNAME_LENGTH_ERROR = "昵称长度必须在1-20个字符之间";
    public static final String PASSWORD_LENGTH_ERROR = "密码长度必须在6-50个字符之间";
    public static final String EMAIL_FORMAT_ERROR = "请输入有效的邮箱地址";
    
    /**
     * 特殊业务场景消息（仍在使用中）
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