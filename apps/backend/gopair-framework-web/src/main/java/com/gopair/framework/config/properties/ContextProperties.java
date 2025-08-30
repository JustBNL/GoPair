package com.gopair.framework.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.List;

/**
 * 
 * 上下文功能属性配置
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

    @Data
    public static class User {
        
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
    }
} 