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
    public static final String LOGIN_SUCCESS = "登录成功";
    public static final String LOGIN_FAILED = "登录失败";
    public static final String USERNAME_OR_PASSWORD_ERROR = "用户名或密码错误";
    public static final String INVALID_CREDENTIALS = "用户名或密码错误";
    public static final String UNAUTHORIZED = "未授权访问";
    public static final String TOKEN_EXPIRED = "令牌已过期";
    public static final String TOKEN_INVALID = "无效的令牌";
    public static final String ACCESS_DENIED = "访问被拒绝";
    public static final String LOGOUT_SUCCESS = "退出登录成功";
    
    /**
     * 用户相关消息
     */
    public static final String USER_NOT_FOUND = "用户不存在";
    public static final String USER_ALREADY_EXISTS = "用户已存在";
    public static final String USERNAME_ALREADY_EXISTS = "用户名已存在";
    public static final String EMAIL_ALREADY_EXISTS = "邮箱已存在";
    public static final String USER_INFO_SUCCESS = "获取用户信息成功";
    public static final String GET_USER_INFO_FAILED = "获取用户信息失败";
    public static final String USER_UPDATE_SUCCESS = "用户信息更新成功";
    public static final String USER_DELETE_SUCCESS = "用户删除成功";
    public static final String REGISTER_FAILED = "注册失败";
    
    /**
     * 参数校验相关消息
     */
    public static final String PARAM_ERROR = "参数错误";
    public static final String PARAM_MISSING = "缺少必要参数";
    public static final String PARAM_TYPE_ERROR = "参数类型错误";
    public static final String PARAM_BIND_ERROR = "参数绑定错误";
    
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
    public static final String OPERATION_FAILED = "操作失败";
    
    /**
     * 权限相关消息
     */
    public static final String ADMIN_PERMISSION = "您拥有管理员权限";
    public static final String USER_PERMISSION = "您拥有用户权限";
    
    /**
     * 私有构造函数，防止实例化
     */
    private MessageConstants() {
        throw new IllegalStateException("常量类不允许实例化");
    }
} 