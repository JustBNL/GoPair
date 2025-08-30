package com.gopair.framework.config.properties;

import com.gopair.common.constants.MessageConstants;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.List;

/**
 * 上下文功能属性配置
 * 
 * 管理用户上下文功能的相关配置项，从 UserContextProperties 迁移而来
 * 
 * @author gopair
 */
@Data
@ConfigurationProperties(prefix = "gopair.context")
public class ContextProperties {

    /**
     * 是否启用上下文功能，默认启用
     */
    private boolean enabled = true;

    /**
     * 用户上下文配置
     */
    private User user = new User();
    
    /**
     * 链路追踪配置
     */
    private Trace trace = new Trace();

    @Data
    public static class User {
        
        /**
         * 是否启用用户上下文功能，默认启用
         */
        private boolean enabled = true;

        /**
         * 排除的路径列表，这些路径不会进行用户上下文处理
         */
        private List<String> excludedPaths = Arrays.asList(
                "/health",
                "/actuator/**",
                "/error",
                "/favicon.ico",
                "/static/**",
                "/webjars/**",
                "/user/login",
                "/user/register"
        );
        
        /**
         * 用户信息头名称
         */
        private String userIdHeader = MessageConstants.HEADER_USER_ID;
        
        /**
         * 用户昵称头名称
         */
        private String nicknameHeader = MessageConstants.HEADER_NICKNAME;
    }
    
    @Data
    public static class Trace {
        
        /**
         * 是否启用链路追踪
         */
        private boolean enabled = true;
        
        /**
         * TraceId 头名称
         */
        private String traceIdHeader = MessageConstants.HEADER_TRACE_ID;
    }
} 