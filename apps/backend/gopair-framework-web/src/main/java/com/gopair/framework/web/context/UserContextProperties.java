package com.gopair.framework.web.context;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.List;

/**
 * 用户上下文配置属性
 * 
 * 管理用户上下文功能的相关配置项
 * 
 * @author gopair
 */
@Data
@ConfigurationProperties(prefix = "gopair.user-context")
public class UserContextProperties {

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
} 